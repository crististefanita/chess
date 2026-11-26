import { assert, test } from './support/atf-node.mjs';
import { createStockfishAnalysis, externalResult, stockfishAnalysisPolicy } from '../src/main/web/stockfish-analysis.mjs';

const context = () => ({ contextKey: 'context-1', initialFen: 'startpos', moves: [],
    rootFen: 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1',
    legalMoves: ['e2e4', 'd2d4'], ply: 0, reviewPly: 1, played: 'd2d4' });
const line = (move, depth, value = 20, rank = 1) => ({ rank, depth, seldepth: depth + 2,
    score: { type: 'cp', value }, wdl: [400, 300, 300], nodes: 1234, timeMs: 8,
    pv: [move], bound: 'EXACT' });
function result(ctx, lines, earlier = [], completeBatch = true) {
    const completedDepthBatches = [...earlier.map(batch => ({ ...batch, expectedPvCount: batch.lines.length, completeBatch: true }))];
    if (completeBatch) completedDepthBatches.push({ depth: lines[0]?.depth, expectedPvCount: lines.length, completeBatch: true, lines });
    return { providerID: 'stockfish-19.0.1-lite-single', version: '19.0.1',
        buildSha256: 'a'.repeat(64), sourceCommit: 'b'.repeat(40), rootFen: ctx.rootFen,
        position: { initialFen: ctx.initialFen, moves: [...ctx.moves] },
        options: { threads: 1, hashMb: 32, nodes: 200_000, limitStrength: false },
        searchFinished: true, bestmove: lines[0]?.pv[0], lines, expectedPvCount: lines.length, completeBatch,
        completedDepthBatches, rawUCITranscript: ['fixture only'] };
}

test('Match projection sends the complete Java context and applies one source-bound DTO', async () => {
    const ctx = context();
    const applied = [];
    const requests = [];
    const worker = createStockfishAnalysis({
        engine: {
            prepareExternalMatchAnalysis: (...args) => { assert.deepEqual(args, ['checkpoint', 0, 7]); return { request: ctx }; },
            applyExternalMatchAnalysis: (revision, key, body) => {
                applied.push({ revision, key, body: JSON.parse(body) }); return JSON.stringify({ analysis: { revision, ply: 0 } });
            },
        },
        analyze: async request => { requests.push(request); return result(ctx, [line('e2e4', 8)]); },
    });
    assert.deepEqual(await worker.match({ checkpoint: 'checkpoint', ply: 0, expectedRevision: 7 }), { analysis: { revision: 7, ply: 0 } });
    assert.equal(requests.length, 1);
    assert.deepEqual(requests[0].moves, []);
    assert.equal(requests[0].rootFen, ctx.rootFen);
    assert.equal(requests[0].nodes, 200_000);
    assert.equal(requests[0].expectedPvCount, 2);
    assert.equal(applied.length, 1);
    assert.equal(applied[0].key, ctx.contextKey);
    assert.equal(applied[0].body.lines[0].nodes, 1234);
    assert.equal(applied[0].body.rawUCITranscript, undefined);
});

test('An interrupted deeper MultiPV cannot displace the last complete top-three batch', async () => {
    const ctx = { ...context(), legalMoves: ['e2e4', 'd2d4', 'c2c4', 'g1f3'] };
    const applied = [];
    const earlier = ['e2e4', 'd2d4', 'c2c4'].map((move, index) => line(move, 12, 20 - index * 5, index + 1));
    const worker = createStockfishAnalysis({
        engine: {
            prepareExternalMatchAnalysis: () => ({ request: ctx }),
            applyExternalMatchAnalysis: (_revision, _key, body) => { applied.push(JSON.parse(body)); return '{"state":"APPLIED"}'; },
        },
        analyze: async () => result(ctx, [line('g1f3', 14, 0, 1)], [{ depth: 12, lines: earlier },
            { depth: 13, lines: [line('g1f3', 13, 20, 1), line('d2d4', 13, 10, 2), line('d2d4', 13, 10, 3)] }], false),
    });
    await worker.match({ checkpoint: 'checkpoint', ply: 0, expectedRevision: 2 });
    assert.equal(applied[0].depth, 12);
    assert.deepEqual(applied[0].lines.map(candidate => candidate.rank), [1, 2, 3]);
    assert.deepEqual(applied[0].lines.map(candidate => candidate.pv[0]), ['e2e4', 'd2d4', 'c2c4']);
});

test('AI match requests exactly the three-line Stockfish allowance', async () => {
    const ctx = { ...context(), legalMoves: ['e2e4', 'd2d4', 'c2c4', 'g1f3', 'b1c3'] };
    const calls = [];
    const applied = [];
    const worker = createStockfishAnalysis({
        engine: {
            prepareExternalMatchAnalysis: () => ({ request: ctx }),
            applyExternalMatchAnalysis: (_revision, _key, body) => { applied.push(JSON.parse(body)); return JSON.stringify({ state: 'APPLIED' }); },
        },
        analyze: async request => {
            calls.push(request);
            return result(ctx, ['e2e4', 'd2d4', 'c2c4'].map((move, index) => line(move, 9, 30 - index * 10, index + 1)));
        },
    });
    const response = await worker.match({ checkpoint: 'checkpoint', ply: 0, expectedRevision: 2 });
    assert.equal(stockfishAnalysisPolicy.multiPV, 3);
    assert.equal(calls.length, 1);
    assert.deepEqual([calls[0].multiPV, calls[0].expectedPvCount], [3, 3]);
    assert.equal(applied[0].lines.length, 3);
    assert.deepEqual(response, { state: 'APPLIED' });
});

test('Study applies an action once and uses the prepared revision, including same-position revisits', async () => {
    const ctx = context();
    let prepared = 0;
    const worker = createStockfishAnalysis({ engine: {
        prepareExternalStudyAnalysis: () => { prepared++; return { revision: 19, request: ctx }; },
        applyExternalStudyAnalysis: (feature, revision, key) => { assert.equal(revision, 19); assert.equal(key, ctx.contextKey); return { feature }; },
    }, analyze: async () => result(ctx, [line('e2e4', 7)]) });
    assert.deepEqual(await worker.study({ feature: 'analysis' }), { feature: 'analysis' });
    assert.equal(prepared, 1);
});

test('A prepared nonanalysis action and a terminal position start no Stockfish request', async () => {
    let queries = 0;
    const state = { fen: 'position', hint: null };
    const worker = createStockfishAnalysis({ engine: {
        prepareExternalStudyAnalysis: () => ({ state, request: null }),
        prepareExternalMatchAnalysis: () => ({ state, request: null }),
    }, analyze: async () => { queries++; throw new Error('unexpected search'); } });
    assert.equal(await worker.study({}), state);
    assert.equal(await worker.match({}), state);
    assert.equal(queries, 0);
});

test('A manual board is returned before cold engine loading and its evaluation is a separate command', async () => {
    let queries = 0;
    const state = { fen: 'legal-board', analysis: true, revision: 1 };
    const worker = createStockfishAnalysis({ engine: {
        prepareExternalStudyAnalysis: () => ({ state, revision: 1, request: context() }),
        applyExternalStudyAnalysis: () => ({ ...state, evaluation: 'Stockfish' }),
    }, analyze: async () => { queries++; return result(context(), [line('e2e4', 8)]); } });
    assert.equal(await worker.study({ feature: 'analysis', action: 'open' }), state);
    assert.equal(queries, 0);
    assert.equal((await worker.study({ feature: 'analysis', action: 'evaluate' })).evaluation, 'Stockfish');
    assert.equal(queries, 1);
});

test('Review compares the actual move at the highest common completed horizon, without mixed depths', async () => {
    const ctx = context();
    const calls = [];
    let saved;
    const progress = [];
    const worker = createStockfishAnalysis({ engine: {
        validateExternalReviewTurn: () => true,
        prepareExternalReview: () => ({ contextKey: 'review-context', requests: [ctx] }),
        applyExternalReview: (checkpoint, digest, body) => { saved = JSON.parse(body); return 'encoded SF review'; },
    }, analyze: async request => {
        calls.push(request);
        return request.searchMoves
            ? result(ctx, [line('d2d4', 5, 5)])
            : result(ctx, [line('e2e4', 6, 40)], [{ depth: 5, lines: [line('e2e4', 5, 20)] }]);
    } });
    assert.equal(await worker.review('checkpoint', 'digest', (...args) => progress.push(args)), 'encoded SF review');
    assert.equal(calls.length, 2);
    assert.deepEqual(calls[1].searchMoves, ['d2d4']);
    assert.equal(calls[1].depth, 6);
    assert.equal(calls[1].nodes, calls[0].nodes);
    assert.equal(calls[1].gameKey, calls[0].gameKey);
    assert.equal(saved.turns[0].ply, 1);
    assert.equal(saved.turns[0].free.depth, 5);
    assert.equal(saved.turns[0].played.depth, 5);
    assert.equal(saved.turns[0].free.lines[0].score.value, 20);
    assert.equal(saved.turns[0].played.bestmove, 'd2d4');
    assert.equal(saved.turns[0].free.optionsIdentity, saved.turns[0].played.optionsIdentity);
    assert.deepEqual(progress, [[0, 1], [1, 1]]);
});

test('Review reuses the same observed best line when it is the recorded move', async () => {
    const ctx = { ...context(), played: 'e2e4' };
    let queries = 0;
    const worker = createStockfishAnalysis({ engine: {
        validateExternalReviewTurn: () => true,
        prepareExternalReview: () => ({ contextKey: 'review-context', requests: [ctx] }),
        applyExternalReview: (_checkpoint, _digest, body) => JSON.parse(body),
    }, analyze: async () => { queries++; return result(ctx, [line('e2e4', 8)]); } });
    const review = await worker.review('checkpoint', 'digest');
    assert.equal(queries, 1);
    assert.deepEqual(review.turns[0].free, review.turns[0].played);
});

test('End-of-game review retains the completed top-three Stockfish batch as evidence', async () => {
    const ctx = context();
    const worker = createStockfishAnalysis({ engine: {
        validateExternalReviewTurn: () => true,
        prepareExternalReview: () => ({ contextKey: 'review-context', requests: [ctx] }),
        applyExternalReview: (_checkpoint, _digest, body) => JSON.parse(body),
    }, analyze: async request => request.searchMoves
        ? result(ctx, [line('d2d4', 8, 35)])
        : result(ctx, [line('e2e4', 8, 40, 1), line('d2d4', 8, 30, 2), line('c2c4', 8, 20, 3)]) });
    const review = await worker.review('checkpoint', 'digest');
    assert.equal(review.turns[0].free.lines.length, 3);
    assert.deepEqual(review.turns[0].free.lines.map(candidate => candidate.rank), [1, 2, 3]);
    assert.deepEqual(review.turns[0].free.lines.map(candidate => candidate.pv[0]), ['e2e4', 'd2d4', 'c2c4']);
    assert.equal(review.turns[0].played.bestmove, 'd2d4');
    assert.equal(review.turns[0].free.depth, review.turns[0].played.depth);
});

test('Review without a common completed horizon publishes no partial review', async () => {
    const ctx = context();
    let applied = 0;
    const worker = createStockfishAnalysis({ engine: {
        validateExternalReviewTurn: () => true,
        prepareExternalReview: () => ({ contextKey: 'review-context', requests: [ctx] }),
        applyExternalReview: () => { applied++; },
    }, analyze: async request => result(ctx, [line(request.searchMoves ? 'd2d4' : 'e2e4', request.searchMoves ? 4 : 5)]) });
    await assert.rejects(worker.review('checkpoint', 'digest'), /no common completed depth/);
    assert.equal(applied, 0);
});

test('Wrong position, different repetition history, unknown source, resource identity and nonexact bounds are rejected', () => {
    const ctx = context();
    const original = result(ctx, [line('e2e4', 5)]);
    const batch = { depth: 5, lines: original.lines };
    for (const mutation of [
        { ...original, rootFen: ctx.rootFen.replace('0 1', '1 1') },
        { ...original, position: { initialFen: 'startpos', moves: ['g1f3', 'g8f6', 'f3g1', 'f6g8'] } },
        { ...original, buildSha256: '' },
        { ...original, providerID: 'hard' },
        { ...original, options: { ...original.options, limitStrength: true } },
        { ...original, searchFinished: false },
    ]) assert.throws(() => externalResult(ctx, mutation, batch));
    assert.throws(() => externalResult(ctx, original, { depth: 5, lines: [{ ...original.lines[0], bound: 'LOWER' }] }));
});

test('Engine errors cannot invoke a native-analysis fallback', async () => {
    let applied = 0;
    const worker = createStockfishAnalysis({ engine: {
        prepareExternalMatchAnalysis: () => ({ request: context() }),
        applyExternalMatchAnalysis: () => { applied++; },
    }, analyze: async () => { throw new Error('engine failed'); } });
    await assert.rejects(worker.match({}), /engine failed/);
    assert.equal(applied, 0);
});
