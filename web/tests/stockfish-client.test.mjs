import { assert, test } from './support/atf-node.mjs';
import { StockfishClient, STOCKFISH_PROVIDER } from '../src/main/web/stockfish-client.mjs';
import { normalizeAnalysisRequest, parseInfoLine, parseBestmove, StockfishIterations } from '../src/main/web/stockfish-protocol.mjs';

const initial = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
const request = (extra = {}) => ({ initialFen: 'startpos', moves: [], rootFen: initial,
    nodes: 200_000, multiPV: 1, expectedPvCount: 1, ...extra });
const handshake = [
    'id name Stockfish 19', 'id author The Stockfish developers',
    'option name Threads type spin default 1 min 1 max 1', 'option name Hash type spin default 16 min 1 max 1024',
    'option name MultiPV type spin default 1 min 1 max 500', 'option name Skill Level type spin default 20 min 0 max 20',
    'option name Ponder type check default false', 'option name UCI_ShowWDL type check default false',
    'option name UCI_LimitStrength type check default false', 'option name EvalFile type string default nn-61e7af4bb97d.nnue', 'uciok',
];
const info = (move = 'e2e4', depth = 6, rank = 1, bound = '') =>
    `info depth ${depth} seldepth ${depth + 2} multipv ${rank} score cp 30 ${bound} wdl 200 700 100 nodes 1234 time 2 pv ${move}`;

function workerType(plan = {}) {
    return class FakeWorker {
        static instances = [];
        constructor(url, options) { this.url = url; this.options = options; this.commands = []; this.terminated = false; this.go = 0; this.constructor.instances.push(this); }
        emit(...lines) { for (const line of lines) this.onmessage?.({ data: line }); }
        postMessage(command) {
            this.commands.push(command);
            queueMicrotask(() => {
                if (this.terminated) return;
                if (command === 'uci') { if (plan.uci !== false) this.emit(...(plan.handshake ?? handshake)); }
                else if (command === 'isready') { if (plan.ready !== false) this.emit('readyok'); }
                else if (command.startsWith('go ')) {
                    this.go++;
                    if (plan.search) plan.search(this, this.go, command);
                    else this.emit(info(), 'bestmove e2e4');
                } else if (command === 'stop') plan.stop?.(this);
            });
        }
        terminate() { this.terminated = true; }
    };
}
const turn = () => new Promise(resolve => setImmediate(resolve));
async function until(predicate) { for (let step = 0; step < 50 && !predicate(); step++) await turn(); assert.ok(predicate()); }

test('Request syntax preserves the complete history and prevents UCI command injection', () => {
    const normalized = normalizeAnalysisRequest(request({ moves: ['g1f3', 'g8f6', 'f3g1', 'f6g8'] }));
    assert.equal(normalized.positionCommand, 'position startpos moves g1f3 g8f6 f3g1 f6g8');
    assert.equal(normalized.goCommand, 'go nodes 200000');
    for (const value of [request({ initialFen: 'startpos\nquit' }), request({ moves: ['e2e4\nquit'] }),
        request({ nodes: 0 }), request({ multiPV: 0 }), request({ searchMoves: [] }), request({ rootFen: initial.split(' ')[0] })]) {
        assert.throws(() => normalizeAnalysisRequest(value));
    }
});

test('Exact cp and mate rows retain side-to-move score, WDL and original work', () => {
    const cp = parseInfoLine(info());
    assert.deepEqual(cp.score, { type: 'cp', value: 30 });
    assert.deepEqual(cp.wdl, [200, 700, 100]);
    assert.equal(cp.nodes, 1234);
    assert.equal(cp.seldepth, 8);
    assert.equal(cp.bound, 'EXACT');
    const mate = parseInfoLine(info().replace('score cp 30', 'score mate -2'));
    assert.deepEqual(mate.score, { type: 'mate', value: -2 });
    assert.equal(parseInfoLine('info depth 7 currmove e2e4 currmovenumber 1'), null);
    assert.deepEqual(parseBestmove('bestmove e2e4 ponder e7e5'), { bestmove: 'e2e4', ponder: 'e7e5' });
    assert.equal(parseBestmove('bestmove 0000').bestmove, null);
});

test('Malformed scores, WDL, PV, bestmove and conflicting bounds cannot produce a result', () => {
    for (const value of [info().replace('200 700 100', '200 700 0'), info().replace('nodes 1234', 'nodes NaN'),
        info().replace('wdl 200 700 100', ''), info().replace('pv e2e4', 'pv e2e9'),
        info('e2e4', 6, 1, 'lowerbound upperbound')]) assert.throws(() => parseInfoLine(value));
    assert.throws(() => parseBestmove('bestmove e2e9'));
    assert.throws(() => parseBestmove('bestmove e2e4 unexpected e7e5'));
});

test('An incomplete final MultiPV round never borrows alternatives from another depth', () => {
    const rows = new StockfishIterations(3, 3);
    for (const [index, move] of ['e2e4', 'd2d4', 'g1f3'].entries()) rows.add(parseInfoLine(info(move, 5, index + 1)));
    rows.add(parseInfoLine(info('e2e4', 6, 1)));
    rows.add(parseInfoLine(info('d2d4', 6, 2, 'lowerbound')));
    const result = rows.finish('e2e4');
    assert.equal(result.depth, 6);
    assert.equal(result.completeBatch, false);
    assert.equal(result.lines.length, 1);
    assert.equal(result.lines[0].depth, 6);
    assert.equal(result.completedDepthBatches[0].depth, 5);
    assert.equal(result.completedDepthBatches[0].lines.length, 3);
    assert.throws(() => rows.finish('a2a3'));
    for (const [index, move] of ['e2e4', 'd2d4', 'd2d4'].entries()) rows.add(parseInfoLine(info(move, 7, index + 1)));
    const duplicated = rows.finish('e2e4');
    assert.equal(duplicated.completeBatch, false);
    assert.deepEqual(duplicated.completedDepthBatches.map(batch => batch.depth), [5]);
    assert.equal(duplicated.iterations.at(-1).completeBatch, false);
});

test('The production Lite client is lazy, classic, uses its unrestricted policy and reuses one worker', async t => {
    const WorkerCtor = workerType();
    const client = new StockfishClient({ WorkerCtor }); t.after(() => client.dispose());
    assert.equal(WorkerCtor.instances.length, 0);
    const first = await client.analyze(request({ gameKey: 'one' }));
    const second = await client.analyze(request({ gameKey: 'one' }));
    assert.equal(WorkerCtor.instances.length, 1);
    const worker = WorkerCtor.instances[0];
    assert.equal(worker.options, undefined);
    assert.equal(first.providerID, STOCKFISH_PROVIDER.id);
    assert.equal(first.buildSha256, STOCKFISH_PROVIDER.buildSha256);
    assert.equal(first.bestmove, 'e2e4');
    assert.equal(second.bestmove, 'e2e4');
    assert.deepEqual(first.position.moves, []);
    assert.equal(first.options.hashMb, 32);
    assert.equal(first.options.limitStrength, false);
    assert.ok(worker.commands.includes('setoption name UCI_LimitStrength value false'));
    assert.ok(worker.commands.includes('setoption name Skill Level value 20'));
    assert.equal(worker.commands.filter(command => command === 'uci').length, 1);
    assert.equal(worker.commands.filter(command => command === 'ucinewgame').length, 1);
    assert.equal(first.proofStatus, 'ENGINE_SEARCH_NOT_EXHAUSTIVE_PROOF');
    assert.ok(first.rawUCITranscript.some(row => row.line === 'go nodes 200000'));
});

test('Cancellation drains the old bestmove before another position or search is submitted', async t => {
    const WorkerCtor = workerType({ search(worker, number) { if (number === 2) worker.emit(info('d7d5'), 'bestmove d7d5'); } });
    const client = new StockfishClient({ WorkerCtor }); t.after(() => client.dispose());
    const controller = new AbortController();
    const first = client.analyze(request({ signal: controller.signal }));
    const rejected = assert.rejects(first, { name: 'AbortError' });
    await until(() => WorkerCtor.instances[0]?.go === 1);
    const worker = WorkerCtor.instances[0];
    const second = client.analyze(request({ moves: ['d2d4'], rootFen: 'rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq d3 0 1' }));
    controller.abort(); await rejected; await turn();
    assert.equal(worker.go, 1);
    assert.equal(worker.commands.at(-1), 'stop');
    worker.emit('bestmove e2e4');
    assert.equal((await second).bestmove, 'd7d5');
    assert.ok(worker.commands.indexOf('stop') < worker.commands.findIndex(command => command.includes('moves d2d4')));
    assert.equal(worker.terminated, false);
});

test('A queued cancellation does not stop another consumer search', async t => {
    const WorkerCtor = workerType({ search() {} });
    const client = new StockfishClient({ WorkerCtor }); t.after(() => client.dispose());
    const first = client.analyze(request());
    await until(() => WorkerCtor.instances[0]?.go === 1);
    const controller = new AbortController();
    const second = client.analyze(request({ signal: controller.signal }));
    const rejected = assert.rejects(second, { name: 'AbortError' }); controller.abort(); await rejected;
    const worker = WorkerCtor.instances[0];
    assert.equal(worker.commands.includes('stop'), false);
    worker.emit(info(), 'bestmove e2e4');
    assert.equal((await first).bestmove, 'e2e4');
    assert.equal(worker.go, 1);
});

test('Wrong engine identity and Full network substitutions are rejected by Lite', async t => {
    for (const changed of [handshake.map(line => line.replace('Stockfish 19', 'Other engine')),
        handshake.map(line => line.replace('nn-61e7af4bb97d.nnue', 'nn-1a298aa575a0.nnue'))]) {
        const WorkerCtor = workerType({ handshake: changed });
        const client = new StockfishClient({ WorkerCtor }); t.after(() => client.dispose());
        await assert.rejects(client.analyze(request()), /identity|lite-network/);
        assert.equal(WorkerCtor.instances[0].terminated, true);
        assert.equal(WorkerCtor.instances[0].go, 0);
    }
});

test('A protocol failure closes its worker and a later request starts cleanly', async t => {
    const WorkerCtor = workerType({ search(worker) {
        if (WorkerCtor.instances.length === 1) worker.emit(info().replace('wdl 200 700 100', 'wdl 999 999 999'));
        else worker.emit(info(), 'bestmove e2e4');
    } });
    const client = new StockfishClient({ WorkerCtor }); t.after(() => client.dispose());
    await assert.rejects(client.analyze(request()), /WDL/);
    assert.equal(WorkerCtor.instances[0].terminated, true);
    const second = await client.analyze(request());
    assert.equal(second.bestmove, 'e2e4');
    assert.equal(WorkerCtor.instances.length, 2);
    assert.ok(!second.rawUCITranscript.some(row => row.line.includes('999 999 999')));
});

test('Missing initialization and unacknowledged stop have bounded recovery', async t => {
    const Missing = workerType({ uci: false });
    const missing = new StockfishClient({ WorkerCtor: Missing, initializationTimeoutMs: 20 }); t.after(() => missing.dispose());
    await assert.rejects(missing.analyze(request()), { name: 'TimeoutError' });
    assert.equal(Missing.instances[0].terminated, true);
    const Stuck = workerType({ search() {} });
    const stuck = new StockfishClient({ WorkerCtor: Stuck, searchTimeoutMs: 20, stopTimeoutMs: 20 }); t.after(() => stuck.dispose());
    await assert.rejects(stuck.analyze(request()), { name: 'TimeoutError' });
    await new Promise(resolve => setTimeout(resolve, 35));
    assert.equal(Stuck.instances[0].terminated, true);
});

test('Disposal settles active and queued jobs and never starts another worker', async () => {
    const WorkerCtor = workerType({ search() {} });
    const client = new StockfishClient({ WorkerCtor });
    const active = client.analyze(request());
    const activeRejected = assert.rejects(active, /disposed/);
    await until(() => WorkerCtor.instances[0]?.go === 1);
    const queued = client.analyze(request());
    const queuedRejected = assert.rejects(queued, /disposed/);
    client.dispose(); await activeRejected; await queuedRejected;
    assert.equal(WorkerCtor.instances[0].terminated, true);
    await assert.rejects(client.analyze(request()), /disposed/);
    assert.equal(WorkerCtor.instances.length, 1);
});
