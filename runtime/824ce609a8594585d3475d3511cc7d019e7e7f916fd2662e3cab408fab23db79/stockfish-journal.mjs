const hash = async text => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))))
    .map(byte => byte.toString(16).padStart(2, '0')).join('');
const position = context => JSON.stringify([context.initialFen, context.moves, context.rootFen, context.played]);

/** Durable complete turns, reusable only with the same full position history and source. */
export async function collectStockfishReview({ checkpoint, contexts, source, existing = null, requireComplete = false,
    inspect, validate, progress = () => {}, checkpointed = () => {} }) {
    let old = [];
    if (existing !== null) {
        const envelope = JSON.parse(existing);
        if (typeof envelope.body !== 'string' || envelope.sha256 !== await hash(envelope.body)) throw new Error('Stockfish journal was modified');
        const journal = JSON.parse(envelope.body);
        if (journal.schema !== 1 || journal.source !== source || !Array.isArray(journal.turns)) throw new Error('Foreign Stockfish journal');
        old = journal.turns;
    }
    const entries = []; const turns = [];
    const encode = async () => {
        const body = JSON.stringify({ schema: 1, source, checkpoint, turns: entries });
        return JSON.stringify({ body, sha256: await hash(body) });
    };
    progress(0, contexts.length);
    for (const context of contexts) {
        const saved = old[context.reviewPly - 1];
        let turn;
        if (saved?.position === position(context) && saved.turn?.ply === context.reviewPly) {
            // The checkpoint may have grown; the engine position still has exactly the same history.
            turn = structuredClone(saved.turn);
            for (const batch of [turn.free, turn.played]) {
                batch.contextKey = context.contextKey; batch.rootFen = context.rootFen;
            }
        } else {
            if (requireComplete) throw new Error('Incomplete or changed Stockfish journal');
            turn = await inspect(context);
        }
        if (!await validate(turn)) throw new Error('Invalid Stockfish turn evidence');
        turns.push(turn);
        entries.push({ position: position(context), turn, observedContextKey: saved?.observedContextKey ?? context.contextKey });
        // Each callback is awaited: a storage error cannot report collection complete.
        if (!requireComplete) await checkpointed(await encode());
        progress(turns.length, contexts.length);
    }
    if (requireComplete && old.length !== contexts.length) throw new Error('Unexpected Stockfish journal turns');
    return { turns, journal: await encode() };
}
