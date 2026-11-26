import { assert, test } from './support/atf-node.mjs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';
import { runtimeDirectory } from '../tools/package-web.mjs';
import { NodeStockfishWorker } from './support/node-stockfish-worker.mjs';

test('Compiled Web ports use the real pinned WASM for hints, openings and persisted review', { timeout: 180000 }, async () => {
    assert.ok(process.env.J7_STOCKFISH_BUNDLE, 'Supply the qualified external Web build');
    const runtime = await runtimeDirectory(process.env.J7_STOCKFISH_BUNDLE);
    const engine = await import(pathToFileURL(join(runtime, 'chess-engine.mjs')));
    const { StockfishClient } = await import(pathToFileURL(join(runtime, 'stockfish-client.mjs')));
    const { createStockfishAnalysis } = await import(pathToFileURL(join(runtime, 'stockfish-analysis.mjs')));
    const children = []; const requests = [];
    let releaseFirst, enteredFirst;
    const startedFirst = new Promise(resolve => { enteredFirst = resolve; });
    const firstGate = new Promise(resolve => { releaseFirst = resolve; });
    let first = true;
    class RealWorker extends NodeStockfishWorker { constructor(url) { super(url); children.push(this); } }
    const client = new StockfishClient({ WorkerCtor: RealWorker });
    const coaching = createStockfishAnalysis({ engine, analyze: async request => {
        if (first) { first = false; enteredFirst(); await firstGate; }
        const observed = await client.analyze(request); requests.push(observed);
        assert.equal(observed.providerID, 'stockfish-19.0.1-lite-single');
        assert.equal(observed.options.limitStrength, false);
        assert.ok(observed.lines.length > 0);
        return observed;
    } });
    const parse = JSON.parse;
    try {
        const checkpoint = engine.canonicalCheckpoint('2|analysis|e2e4');
        const nativeBefore = engine.computeBotMove(checkpoint);
        const pendingHint = coaching.match({ checkpoint, ply: 1, expectedRevision: 5 });
        await startedFirst;
        assert.equal(engine.computeBotMove(checkpoint), nativeBefore, 'a pending Stockfish request cannot block or alter the actual compiled Native opponent');
        releaseFirst();
        const match = await pendingHint;
        assert.equal(match.outcome, 'APPLIED');
        assert.equal(match.analysis.engine.providerID, 'stockfish-19.0.1-lite-single');
        assert.equal(match.analysis.hint.count, 3, 'the close top-three roots are selectable in an actual AI-game hint');
        const complete = requests[0].completedDepthBatches.at(-1);
        assert.equal(complete.completeBatch, true);
        const wdl = complete.lines[0].wdl;
        assert.equal(match.analysis.evaluation.whiteShare, (1000 - Math.round(wdl[0] + wdl[1] / 2)) / 1000);
        const hinted = parse(engine.matchAnalysisAction(5, 'hint', 0));
        assert.equal(hinted.analysis.hint.move, requests[0].bestmove);
        engine.prepareExternalMatchAnalysis(checkpoint, 0, 6);
        assert.equal(parse(engine.applyExternalMatchAnalysis(5, 'old', '{}')).outcome, 'STALE');

        const opened = await coaching.study({ feature: 'analysis', action: 'open', value: checkpoint, index: 1, saved: '', option: '', locale: 'en' });
        assert.equal(opened.ply, 1);
        assert.equal(opened.evaluation, null);
        const study = await coaching.study({ feature: 'analysis', action: 'evaluate', value: '', index: opened.revision, saved: '', option: '', locale: 'en' });
        assert.equal(study.evaluation.engine.providerID, 'stockfish-19.0.1-lite-single');
        const old = parse(engine.prepareExternalStudyAnalysis('analysis', 'open', checkpoint, 1, '', '', 'en'));
        engine.prepareExternalStudyAnalysis('analysis', 'seek', '', 0, '', '', 'en');
        engine.prepareExternalStudyAnalysis('analysis', 'seek', '', 1, '', '', 'en');
        assert.equal(parse(engine.applyExternalStudyAnalysis('analysis', old.revision, old.request.contextKey, '{}')).evaluation, null);

        const lesson = await coaching.study({ feature: 'openings', action: 'START', value: 'italian', index: 0, saved: '', option: 'en', locale: 'en' });
        const enabled = await coaching.study({ feature: 'openings', action: 'AI_TOGGLE', value: '', index: 0, saved: lesson.saved, option: 'en', locale: 'en' });
        const opening = await coaching.study({ feature: 'openings', action: 'ANALYZE', value: '', index: 0, saved: enabled.saved, option: 'en', locale: 'en' });
        assert.equal(opening.engine.providerID, 'stockfish-19.0.1-lite-single');
        assert.ok(opening.variants.length >= 1);

        const mate = engine.canonicalCheckpoint('2|analysis|f2f3 e7e5 g2g4 d8h4');
        const digest = createHash('sha256').update(mate).digest('base64');
        const progress = [];
        const savedTurns = [];
        const journal = await coaching.prepareReview(mate, digest, (done, total) => progress.push([done, total]), null, value => savedTurns.push(value));
        const beforeAggregation = requests.length;
        const review = await coaching.review(mate, digest, () => {}, { journal });
        assert.equal(requests.length, beforeAggregation);
        assert.equal(savedTurns.length, 4);
        assert.deepEqual(progress.at(-1), [4, 4]);
        const first = parse(engine.openReview('fixture-sf', mate, review, null, digest));
        const reopened = parse(engine.openReview('fixture-sf', mate, review, first.bookmark, digest));
        assert.equal(reopened.engine.providerID, 'stockfish-19.0.1-lite-single');
        assert.deepEqual(reopened.moves, first.moves);
        assert.equal(reopened.moves.length, 4);
        assert.equal(reopened.rated, false);
        const envelope = JSON.parse(journal); const raw = JSON.parse(envelope.body);
        assert.equal(raw.turns[0].turn.free.lines.length, 3, 'the completed review retains the close Stockfish top-three batch');
        assert.equal(raw.turns[0].turn.played.lines.length, 1, 'the recorded move comparison remains a separate exact root');
        raw.turns[0].turn.free.lines[0].pv = ['e2e5'];
        const body = JSON.stringify(raw);
        await assert.rejects(coaching.review(mate, digest, () => {}, { journal: JSON.stringify({ body, sha256: createHash('sha256').update(body).digest('hex') }) }));
        assert.equal(requests.length, beforeAggregation);
        assert.equal(children.length, 1);
    } finally {
        for (const child of children) {
            const exit = await child.finish();
            assert.deepEqual(exit, { code: 0, signal: null });
        }
        client.dispose();
    }
});
