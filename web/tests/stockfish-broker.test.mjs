import { assert, test } from './support/atf-node.mjs';
import { createEngineWorker, disposeStockfishBroker } from '../src/main/web/stockfish-broker.mjs';
import { STOCKFISH_PROVIDER } from '../src/main/web/stockfish-client.mjs';

const fen = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
const query = (extra = {}) => ({ initialFen: 'startpos', moves: [], rootFen: fen,
    nodes: 200000, multiPV: 1, expectedPvCount: 1, ...extra });
function answer(request, extra = {}) {
    return { providerID: STOCKFISH_PROVIDER.id, buildSha256: STOCKFISH_PROVIDER.buildSha256,
        version: STOCKFISH_PROVIDER.version, sourceCommit: STOCKFISH_PROVIDER.sourceCommit,
        rootFen: request.rootFen, position: { initialFen: request.initialFen, moves: [...request.moves] },
        options: { threads: 1, hashMb: 32, nodes: request.nodes, limitStrength: false }, searchFinished: true, completeBatch: true,
        depth: 5, lines: [{ rank: 1, depth: 5, bound: 'EXACT', score: { type: 'cp', value: 20 },
            wdl: [100, 800, 100], pv: ['e2e4'] }], ...extra };
}
class JavaWorker {
    static instances = [];
    constructor(url, options) { this.url = url; this.options = options; this.events = new Map(); this.posts = []; this.constructor.instances.push(this); }
    addEventListener(type, listener) { if (!this.events.has(type)) this.events.set(type, new Set()); this.events.get(type).add(listener); }
    removeEventListener(type, listener) { this.events.get(type)?.delete(listener); }
    postMessage(data) { this.posts.push(data); }
    emit(data) { const event = { data }; for (const listener of this.events.get('message') ?? []) listener(event); this.onmessage?.(event); }
    terminate() { this.terminated = true; }
}
const tick = () => new Promise(resolve => setImmediate(resolve));
const send = (worker, id, request = query()) => worker.emit({ kind: 'j7-stockfish-request', requestId: id, request });
function fixture(t, analyze = async request => answer(request)) {
    const calls = [];
    const client = { analyze(request) { calls.push(request); return analyze(request); }, dispose() { this.disposed = true; } };
    const facade = createEngineWorker({ WorkerCtor: JavaWorker, client });
    const worker = JavaWorker.instances.at(-1);
    t.after(() => { facade.terminate(); disposeStockfishBroker(); });
    return { facade, worker, client, calls };
}

test('Private Stockfish RPC is invisible to UI listeners while normal Worker events remain unchanged', async t => {
    const { facade, worker, calls } = fixture(t);
    const events = [];
    const assigned = [];
    const listener = event => events.push(event.data);
    facade.addEventListener('message', listener);
    facade.onmessage = event => assigned.push(event.data);
    send(worker, 'one'); await tick();
    assert.equal(calls.length, 1);
    assert.equal(events.length, 0);
    assert.equal(assigned.length, 0);
    assert.equal(worker.posts.at(-1).result.providerID, STOCKFISH_PROVIDER.id);
    assert.equal(worker.options.type, 'module');
    worker.emit({ id: 'UI', state: 'ready' });
    assert.deepEqual(events, [{ id: 'UI', state: 'ready' }]);
    assert.deepEqual(assigned, events);
    facade.removeEventListener('message', listener);
    worker.emit({ id: 'second' });
    assert.equal(events.length, 1);
    assert.equal(assigned.length, 2);
    facade.postMessage({ id: 'command', type: 'snapshot' });
    assert.deepEqual(worker.posts.at(-1), { id: 'command', type: 'snapshot' });
});

test('An exact A to B to A revisit reuses only completed results and isolates consumer mutations', async t => {
    const { worker, calls } = fixture(t);
    send(worker, 'a'); await tick();
    const first = worker.posts.at(-1).result;
    first.lines[0].pv[0] = 'a2a4';
    send(worker, 'b', query({ rootFen: fen.replace('0 1', '1 1') })); await tick();
    send(worker, 'a-again'); await tick();
    assert.equal(calls.length, 2);
    const repeated = worker.posts.at(-1).result;
    assert.equal(repeated.cacheReuse, true);
    assert.equal(repeated.lines[0].pv[0], 'e2e4');
    assert.equal(repeated.cacheOf, first.queryId);
    repeated.lines[0].pv[0] = 'b2b4';
    send(worker, 'third'); await tick();
    assert.equal(worker.posts.at(-1).result.lines[0].pv[0], 'e2e4');
});

test('Different history, resource allowance and forced roots never reuse another query', async t => {
    const { worker, calls } = fixture(t);
    for (const [index, request] of [query(), query({ moves: ['g1f3', 'g8f6', 'f3g1', 'f6g8'] }),
        query({ nodes: 300000 }), query({ searchMoves: ['d2d4'] }), query({ depth: 7 })].entries()) {
        send(worker, String(index), request); await tick();
        assert.equal(worker.posts.at(-1).result.cacheReuse, false);
    }
    assert.equal(calls.length, 5);
});

test('Cancelling one Java consumer does not cancel another consumer or publish its late result', async t => {
    const pending = [];
    const client = { analyze(request) { return new Promise(resolve => pending.push({ request, resolve })); } };
    const one = createEngineWorker({ WorkerCtor: JavaWorker, client });
    const first = JavaWorker.instances.at(-1);
    const two = createEngineWorker({ WorkerCtor: JavaWorker, client });
    const second = JavaWorker.instances.at(-1);
    t.after(() => { one.terminate(); two.terminate(); disposeStockfishBroker(); });
    send(first, 'same'); send(second, 'same'); await tick();
    one.terminate();
    assert.equal(pending[0].request.signal.aborted, true);
    assert.equal(pending[1].request.signal.aborted, false);
    pending[0].resolve(answer(pending[0].request)); pending[1].resolve(answer(pending[1].request)); await tick();
    assert.equal(first.posts.length, 0);
    assert.equal(second.posts.at(-1).result.providerID, STOCKFISH_PROVIDER.id);
});

test('Foreign cancellation and duplicate IDs cannot replace another active result', async t => {
    let resolve;
    const { worker, calls } = fixture(t, request => new Promise(done => { resolve = () => done(answer(request)); }));
    send(worker, 'live'); await tick();
    worker.emit({ kind: 'j7-stockfish-cancel', requestId: 'foreign' });
    assert.equal(calls[0].signal.aborted, false);
    send(worker, 'live'); await tick();
    assert.equal(calls[0].signal.aborted, true);
    assert.match(worker.posts.at(-1).error, /Duplicate/);
    resolve(); await tick();
    assert.equal(worker.posts.filter(message => message.result).length, 0);
});

test('Malformed requests and engine failures do not create cached success', async t => {
    const { worker, calls } = fixture(t, async () => { throw new Error('engine failed'); });
    send(worker, 'malformed', { ...query(), moves: ['e2e4\nquit'] }); await tick();
    assert.equal(calls.length, 0);
    assert.match(worker.posts.at(-1).error, /Invalid/);
    send(worker, 'valid'); await tick();
    send(worker, 'retry'); await tick();
    assert.equal(calls.length, 2);
    assert.match(worker.posts.at(-1).error, /engine failed/);
});

test('Incomplete or substituted-engine results remain uncached', async t => {
    const { worker, calls } = fixture(t, async request => answer(request, { completeBatch: false }));
    send(worker, 'one'); await tick(); send(worker, 'two'); await tick();
    assert.equal(calls.length, 2);
    const other = fixture(t, async request => answer(request, { buildSha256: '0'.repeat(64) }));
    send(other.worker, 'one'); await tick(); send(other.worker, 'two'); await tick();
    assert.equal(other.calls.length, 2);
});

test('The completed result cache is a bounded least-recently-used store', async t => {
    const { worker, calls } = fixture(t);
    for (let index = 0; index < 65; index++) {
        send(worker, String(index), query({ rootFen: fen.replace('0 1', index + ' 1') })); await tick();
    }
    assert.equal(calls.length, 65);
    send(worker, 'last', query({ rootFen: fen.replace('0 1', '64 1') })); await tick();
    assert.equal(calls.length, 65);
    send(worker, 'evicted'); await tick();
    assert.equal(calls.length, 66);
});

test('Page disposal closes the analysis client and clears its completed cache', async t => {
    const { worker, client, calls } = fixture(t);
    send(worker, 'first'); await tick();
    disposeStockfishBroker();
    assert.equal(client.disposed, true);
    send(worker, 'after'); await tick();
    assert.equal(calls.length, 2);
    assert.equal(worker.posts.at(-1).result.cacheReuse, false);
});
