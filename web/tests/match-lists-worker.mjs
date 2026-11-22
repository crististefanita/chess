import assert from 'node:assert/strict';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { runtimeDirectory } from '../tools/package-web.mjs';

const site = process.argv[2];
assert.ok(site, 'Provide the external Web build');
let receive;
globalThis.self = { postMessage: reply => receive(reply) };
await import(pathToFileURL(join(await runtimeDirectory(site), 'engine-worker.mjs')));

async function call(type, fields = {}) {
    const reply = await new Promise(resolve => {
        receive = resolve;
        self.onmessage({ data: { id: type, type, ...fields } });
    });
    assert.equal(reply.error, undefined, reply.error);
    return reply;
}

const start = () => call('match-open', { setup: { mode: 'AI', humanSide: 'WHITE', level: 'BASIC', analysis: false } });
await call('ledger-open');
const first = await start();
const replacedId = first.saved[0].id;
let state = first;
for (let count = 2; count <= 10; count++) state = await start();
assert.equal(state.saved.length, 10);
assert.equal(state.finished.length, 0);
state = await start();
assert.equal(state.saved.length, 10);
assert.equal(state.saved.some(row => row.id === replacedId), false, 'the eleventh active game replaces the oldest');

let oldestResult;
for (let count = 1; count <= 11; count++) {
    state = await call('match-play', { expectedRevision: state.match.revision, move: 'e2e4' });
    state = await call('match-decision', { expectedRevision: state.match.revision, action: 'RESIGN' });
    assert.equal(state.outcome, 'APPLIED');
    assert.equal(state.saved.length, 9, 'a completed game leaves Saved in the same transaction');
    assert.equal(state.finished.length, Math.min(count, 10));
    if (!oldestResult) oldestResult = state.finished[0].id;
    state = await start();
}
assert.equal(state.saved.length, 10);
assert.equal(state.finished.length, 10);
assert.equal(state.finished.some(row => row.id === oldestResult), false, 'the eleventh result evicts only the oldest');
assert.equal(state.saved.every(row => row.result === 'IN_PROGRESS'), true);
assert.equal(state.finished.every(row => row.result !== 'IN_PROGRESS'), true);
const restored = await call('ledger-open', { ledger: state.ledger });
assert.deepEqual(restored.saved, state.saved);
assert.deepEqual(restored.finished, state.finished);
console.log('PASS Ten Saved and ten Finished rows remain separate after replacement and worker reload');
