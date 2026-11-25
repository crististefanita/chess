import assert from 'node:assert/strict';
import test from 'node:test';
import { createReviewCollection, createReviewProgressStore } from '../src/main/web/review-progress.mjs';

const tick = () => new Promise(resolve => setImmediate(resolve));
function storage() {
  const values = new Map();
  return { values, getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
}

test('Restart keeps journals by game ID and library eviction removes their records', () => {
  const disk = storage(); let ids = ['a','b'];
  const store = createReviewProgressStore(disk, () => ids);
  assert.equal(store.save({ id: 'a', checkpoint: 'before' }, 'work-a', null), true);
  assert.equal(store.save({ id: 'b', checkpoint: 'other' }, 'work-b', null), true);
  const restarted = createReviewProgressStore(disk, () => ids);
  assert.equal(restarted.load('a'), 'work-a'); assert.equal(restarted.load('b'), 'work-b');
  ids = ['b']; restarted.prune();
  assert.equal(restarted.load('a'), null); assert.equal(restarted.load('b'), 'work-b');
});

test('A stale writer and an evicted owner cannot overwrite durable work', () => {
  const disk = storage(); let ids = ['a']; const store = createReviewProgressStore(disk, () => ids);
  const request = { id: 'a', checkpoint: 'first' };
  assert.equal(store.save(request, 'fresh', null), true);
  assert.equal(store.save(request, 'stale', null), false);
  assert.equal(store.load('a'), 'fresh'); ids = [];
  assert.equal(store.save(request, 'late', 'fresh'), false);
});

test('The final review joins one in-flight calculation and waits for durable save', async () => {
  const done = []; let finishSave; const requests = []; let calculations = 0;
  const collection = createReviewCollection({ load: () => 'previous', compute: request => {
    calculations++; requests.push(request); return new Promise(resolve => done.push(resolve));
  }, save: () => new Promise(resolve => { finishSave = resolve; }), failed: assert.fail });
  const request = { id: 'game', checkpoint: 'move-2' };
  collection.submit(request);
  let accepted = false; const final = collection.ensure(request).then(value => { accepted = true; return value; });
  assert.equal(calculations, 1); assert.equal(requests[0].journal, 'previous');
  done[0]('complete'); await tick(); assert.equal(accepted, false);
  finishSave(true); assert.equal(await final, 'complete');
  assert.equal(await collection.ensure(request), 'complete'); assert.equal(calculations, 1);
});

test('Rapid background moves coalesce while all foreground review requests remain scheduled', async () => {
  const done = []; const seen = [];
  const collection = createReviewCollection({ load: () => null, compute: request => {
    seen.push(request.checkpoint); return new Promise(resolve => done.push(resolve));
  }, save: () => true, failed: assert.fail });
  const request = checkpoint => ({ id: 'game', checkpoint });
  collection.submit(request('1')); collection.submit(request('2')); collection.submit(request('3'));
  const a = collection.ensure(request('3')); const b = collection.ensure(request('4'));
  done[0]('one'); await tick(); done[1]('three'); await tick(); done[2]('four');
  assert.deepEqual(await Promise.all([a,b]), ['three','four']); assert.deepEqual(seen, ['1','3','4']);
});

test('A storage failure rejects final aggregation and permits a later retry', async () => {
  let attempts = 0; const errors = [];
  const collection = createReviewCollection({ load: () => null, compute: async () => 'native-work',
    save: () => { if (++attempts === 1) throw new Error('quota'); return true; }, failed: error => errors.push(error.message) });
  const request = { id: 'game', checkpoint: 'move' };
  await assert.rejects(collection.ensure(request), /quota/);
  assert.equal(await collection.ensure(request), 'native-work'); assert.deepEqual(errors, ['quota']);
});

test('Malformed stored data is a miss and a denied write is never a cache hit', () => {
  const disk = storage(); disk.values.set('chesslab.review-work.v1', '{bad');
  const store = createReviewProgressStore(disk, () => ['a']); assert.equal(store.load('a'), null);
  disk.setItem = () => { throw new Error('denied'); };
  assert.throws(() => store.save({ id: 'a', checkpoint: 'move' }, 'work', null), /denied/);
  assert.equal(store.load('a'), null);
});

test('Each completed ply survives a later calculation failure and is reused after restart', async () => {
  let journal = null; let attempt = 0; const seen = []; const failures = [];
  const collection = createReviewCollection({ load: () => journal,
    compute: async request => {
      seen.push(request.journal);
      if (++attempt === 1) { await request.checkpointed('ply-1'); throw new Error('interrupted at ply-2'); }
      assert.equal(request.journal, 'ply-1'); await request.checkpointed('ply-2'); return 'ply-2';
    },
    save: async (_, work, expected) => { assert.equal(expected, journal); journal = work; return true; },
    failed: error => failures.push(error.message) });
  const request = { id: 'game', checkpoint: 'two-moves' };
  await assert.rejects(collection.ensure(request), /interrupted/);
  assert.equal(journal, 'ply-1');
  assert.equal(await collection.ensure(request), 'ply-2');
  assert.deepEqual(seen, [null, 'ply-1']);
  assert.deepEqual(failures, ['interrupted at ply-2']);
});

test('An incomplete asynchronous checkpoint write prevents final aggregation', async () => {
  let finishSave; let ready = false;
  const collection = createReviewCollection({ load: () => null,
    compute: async request => { void request.checkpointed('complete'); return 'complete'; },
    save: () => new Promise(resolve => { finishSave = resolve; }), failed: () => {} });
  const finished = collection.ensure({ id: 'game', checkpoint: 'one' }).then(() => { ready = true; });
  await tick(); assert.equal(ready, false); finishSave(true); await finished; assert.equal(ready, true);
});
