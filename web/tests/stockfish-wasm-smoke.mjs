import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { dirname, isAbsolute, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { StockfishClient } from '../src/main/web/stockfish-client.mjs';
import { verifyStockfishPackage } from '../tools/verify-stockfish.mjs';
import { NodeStockfishWorker } from './support/node-stockfish-worker.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
if (!process.argv[2]) throw new Error('Supply an evidence directory outside the source checkout');
const output = resolve(process.argv[2]);
const outside = relative(root, output);
if (!outside || !isAbsolute(outside) && outside !== '..' && !outside.startsWith('..' + (process.platform === 'win32' ? '\\' : '/'))) {
    throw new Error('Keep native evidence outside the source checkout');
}
await mkdir(output, { recursive: true });
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const assets = new Map();
const directory = join(root, 'web/src/main/web/stockfish');
for (const name of await readdir(directory)) assets.set('stockfish/' + name, await readFile(join(directory, name)));
const pinned = verifyStockfishPackage(assets);
const workers = [];
class ObservedWorker extends NodeStockfishWorker { constructor(url) { super(url); workers.push(this); } }
const client = new StockfishClient({ WorkerCtor: ObservedWorker });
const runId = randomUUID();
const startedUtc = new Date().toISOString();
const deadline = setTimeout(() => client.dispose(), 120_000);
const observed = [];
let nativeExit = 1;
let error;
try {
    for (const request of [
        { name: 'Initial black reply after 1.e4', initialFen: 'startpos', moves: ['e2e4'],
            rootFen: 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1',
            multiPV: 3, expectedPvCount: 3 },
        { name: 'Fool mate is a real mate result', initialFen: 'startpos', moves: ['f2f3', 'e7e5', 'g2g4'],
            rootFen: 'rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2',
            multiPV: 1, expectedPvCount: 1 },
    ]) {
        const result = await client.analyze({ ...request, nodes: 200_000, gameKey: request.name });
        assert.equal(result.providerID, pinned.providerID);
        assert.equal(result.buildSha256, pinned.files.find(file => file.path.endsWith('.wasm')).sha256);
        assert.equal(result.searchFinished, true);
        assert.ok(result.lines.length > 0 && result.lines.every(line => line.bound === 'EXACT'));
        assert.equal(result.lines[0].pv[0], result.bestmove);
        if (request.name.startsWith('Fool')) {
            assert.equal(result.bestmove, 'd8h4');
            assert.equal(result.lines[0].mate, 1);
        }
        observed.push({ request, result });
    }
    assert.equal(workers.length, 1, 'The same real WASM worker serves both positions');
    const exit = await workers[0].finish();
    assert.deepEqual(exit, { code: 0, signal: null });
    nativeExit = 0;
} catch (failure) {
    error = String(failure.stack ?? failure);
} finally {
    clearTimeout(deadline);
    client.dispose();
    for (const worker of workers) { if (!worker.exit) worker.terminate(); await worker.closed; }
    const resultBytes = JSON.stringify({ schema: 1, runId, scope: 'Actual pinned WASM through production UCI client; Node transport, not browser rendering',
        pinned, observed, children: workers.map(worker => ({ pid: worker.child.pid, exit: worker.exit, commands: worker.commands,
            stderr: worker.stderr, transcript: worker.transcript })) }, null, 2) + '\n';
    await writeFile(join(output, 'results.json'), resultBytes);
    const head = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim();
    const dirty = execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim().length > 0;
    await writeFile(join(output, 'native-exit.json'), JSON.stringify({ schema: 1, nativeExit, runId, startedUtc,
        finishedUtc: new Date().toISOString(), queries: observed.length, checkoutHead: head, workingTreeDirty: dirty,
        producerCommit: dirty ? null : head, resultsSha256: sha(resultBytes), error: error ?? null }, null, 2) + '\n');
}
if (nativeExit) { console.error(error); process.exitCode = nativeExit; }
else console.log('PINNED_REAL_STOCKFISH_WASM_NATIVE_0 ' + output);
