import { collectStockfishReview } from './stockfish-journal.mjs';
import { STOCKFISH_PROVIDER } from './stockfish-client.mjs';
export const stockfishAnalysisPolicy = Object.freeze({ nodes: 200_000, multiPV: 3 });

const parse = value => typeof value === 'string' ? JSON.parse(value) : value;
const exact = line => line?.bound === 'EXACT' && Number.isInteger(line.depth) && line.depth > 0
    && Array.isArray(line.pv) && line.pv.length > 0;

function batches(result) {
    const found = new Map();
    for (const batch of result.completedDepthBatches ?? []) {
        const lines = batch.lines?.filter(exact).sort((a, b) => a.rank - b.rank);
        if (lines?.length && lines[0].rank === 1 && lines.every(line => line.depth === batch.depth)) {
            found.set(batch.depth, { depth: batch.depth, lines });
        }
    }
    if (result.lines?.length && result.lines.every(exact) && result.lines[0].rank === 1
        && result.lines.every(line => line.depth === result.lines[0].depth)) {
        found.set(result.lines[0].depth, { depth: result.lines[0].depth, lines: result.lines });
    }
    return found;
}

export function externalResult(context, result, batch) {
    if (!result?.searchFinished || result.rootFen !== context.rootFen) throw new Error('Stockfish returned another position');
    if (result.position?.initialFen !== context.initialFen
        || JSON.stringify(result.position?.moves) !== JSON.stringify(context.moves)) {
        throw new Error('Stockfish returned another game history');
    }
    if (typeof result.providerID !== 'string' || !result.providerID.startsWith('stockfish-')
        || typeof result.version !== 'string' || !/^[a-f0-9]{64}$/.test(result.buildSha256)
        || !/^[a-f0-9]{40}$/.test(result.sourceCommit)) throw new Error('Stockfish source identity is missing');
    if (!batch?.lines?.length || !batch.lines.every((line, index) => exact(line)
        && line.depth === batch.depth && line.rank === index + 1)
        || new Set(batch.lines.map(line => line.pv[0])).size !== batch.lines.length) {
        throw new Error('Stockfish has no completed exact result');
    }
    const options = result.options;
    if (!options || options.threads !== 1 || options.limitStrength !== false || !Number.isInteger(options.nodes)
        || options.nodes < 1 || !Number.isInteger(options.hashMb) || options.hashMb < 1) {
        throw new Error('Stockfish analysis resource identity is missing');
    }
    return {
        providerID: result.providerID, version: result.version, buildSha256: result.buildSha256,
        sourceCommit: result.sourceCommit,
        optionsIdentity: JSON.stringify({ threads: options.threads, hashMb: options.hashMb,
            nodes: options.nodes, limitStrength: options.limitStrength }),
        contextKey: context.contextKey, rootFen: context.rootFen, depth: batch.depth,
        bestmove: batch.lines[0].pv[0],
        lines: batch.lines.map(line => ({ rank: line.rank, depth: line.depth,
            score: line.score, wdl: line.wdl, pv: [...line.pv], bound: line.bound,
            nodes: line.nodes, seldepth: line.seldepth, timeMs: line.timeMs })),
    };
}

/** Async transport only. Java remains the authority for positions and projected views. */
export function createStockfishAnalysis({ engine, analyze, policy = stockfishAnalysisPolicy }) {
    if (!Number.isInteger(policy.nodes) || policy.nodes < 1 || !Number.isInteger(policy.multiPV)
        || policy.multiPV < 1 || policy.multiPV > 5) throw new TypeError('Invalid Stockfish analysis policy');

    async function search(context, options = {}) {
        if (!context || typeof context.contextKey !== 'string' || typeof context.rootFen !== 'string'
            || !Array.isArray(context.moves) || !Array.isArray(context.legalMoves) || !context.legalMoves.length) {
            throw new Error('Missing playable Java analysis context');
        }
        const multiPV = Math.min(options.multiPV ?? policy.multiPV, context.legalMoves.length);
        return analyze({ initialFen: context.initialFen, moves: context.moves, rootFen: context.rootFen,
            gameKey: options.gameKey ?? context.contextKey, nodes: policy.nodes, multiPV,
            expectedPvCount: options.searchMoves ? 1 : multiPV,
            ...(options.depth == null ? {} : { depth: options.depth }),
            ...(options.searchMoves ? { searchMoves: options.searchMoves } : {}) });
    }

    function latest(context, result) {
        const available = batches(result);
        const depth = Math.max(...available.keys());
        return externalResult(context, result, available.get(depth));
    }

    async function inspectReview(context, gameKey) {
        const free = await search(context, { gameKey: gameKey });
        const freeBatches = batches(free);
        const last = freeBatches.get(Math.max(...freeBatches.keys()));
        if (!last) throw new Error('Stockfish did not complete the free comparison');
        let played;
        let common = last.depth;
        if (last.lines[0].pv[0] === context.played) {
            played = externalResult(context, free, { depth: common, lines: [last.lines[0]] });
        } else {
            const forced = await search(context, { gameKey: gameKey,
                searchMoves: [context.played], multiPV: 1, depth: last.depth });
            const forcedBatches = batches(forced);
            const depths = [...freeBatches.keys()].filter(depth => forcedBatches.has(depth)).sort((a, b) => b - a);
            if (!depths.length) throw new Error('Stockfish comparisons have no common completed depth');
            common = depths[0];
            played = externalResult(context, forced, forcedBatches.get(common));
            if (played.bestmove !== context.played) throw new Error('Stockfish did not evaluate the recorded move');
        }
        const best = externalResult(context, free, freeBatches.get(common));
        if (best.optionsIdentity !== played.optionsIdentity || best.providerID !== played.providerID
            || best.buildSha256 !== played.buildSha256) throw new Error('Stockfish review sources differ');

        return { ply: context.reviewPly, free: best, played };
    }

    async function collect(checkpoint, digest, progress, journal, requireComplete, checkpointed) {
        const prepared = parse(engine.prepareExternalReview(checkpoint, digest));
        const gathered = await collectStockfishReview({ checkpoint, contexts: prepared.requests,
            source: JSON.stringify([STOCKFISH_PROVIDER.id, STOCKFISH_PROVIDER.buildSha256, policy]),
            existing: journal, requireComplete, progress, checkpointed,
            inspect: context => inspectReview(context, prepared.contextKey),
            validate: turn => engine.validateExternalReviewTurn(checkpoint, turn.ply, JSON.stringify(turn.free), JSON.stringify(turn.played)),
        });
        return { ...gathered, contextKey: prepared.contextKey };
    }

    return {
        async match({ checkpoint, ply, expectedRevision }) {
            const prepared = parse(engine.prepareExternalMatchAnalysis(checkpoint, ply, expectedRevision));
            if (!prepared.request) return prepared.state;
            const result = latest(prepared.request, await search(prepared.request));
            return parse(engine.applyExternalMatchAnalysis(expectedRevision, prepared.request.contextKey, JSON.stringify(result)));
        },

        async study({ feature, action, value, index, saved, option, locale }) {
            const prepared = parse(engine.prepareExternalStudyAnalysis(feature, action, value, index, saved, option, locale));
            // Publish the legal board immediately. The existing study session schedules its
            // separate evaluate command, so cold WASM loading cannot leave an empty board.
            if (feature === 'analysis' && typeof action === 'string' && action !== 'evaluate') return prepared.state;
            if (!prepared.request) return prepared.state;
            const result = latest(prepared.request, await search(prepared.request));
            return parse(engine.applyExternalStudyAnalysis(feature, prepared.revision, prepared.request.contextKey, JSON.stringify(result)));
        },

        async prepareReview(checkpoint, digest, progress = () => {}, journal = null, checkpointed = () => {}) {
            return (await collect(checkpoint, digest, progress, journal, false, checkpointed)).journal;
        },

        async review(checkpoint, digest, progress = () => {}, { rated = false, journal = null } = {}) {
            const result = await collect(checkpoint, digest, progress, journal, journal !== null);
            return engine.applyExternalReview(checkpoint, digest, JSON.stringify({ contextKey: result.contextKey, rated, turns: result.turns }));
        },
    };
}
