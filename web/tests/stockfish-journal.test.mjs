import { assert, test } from './support/atf-node.mjs';
import { collectStockfishReview } from '../src/main/web/stockfish-journal.mjs';
const contexts = (n, key = 'game') => Array.from({ length: n }, (_, index) => ({ reviewPly: index + 1,
    contextKey: key + '-' + index, initialFen: 'startpos', rootFen: 'root-' + index,
    moves: Array.from({ length: index }, (_, i) => 'move-' + i), played: 'move-' + index }));
const turn = context => ({ ply: context.reviewPly, free: { contextKey: context.contextKey, rootFen: context.rootFen, score: 10 },
    played: { contextKey: context.contextKey, rootFen: context.rootFen, score: 5 } });
const defaults = () => ({ checkpoint: 'game', contexts: contexts(2), source: 'pinned-source',
    inspect: async context => turn(context), validate: row => row.free.score >= row.played.score });

test('Stockfish journals persist complete turns and reuse exact prefixes when a game grows', async () => {
    const saved = []; const first = await collectStockfishReview({ ...defaults(), checkpointed: value => saved.push(value) });
    assert.equal(saved.length, 2);
    let queries = 0;
    const grown = await collectStockfishReview({ ...defaults(), checkpoint: 'grown', contexts: contexts(3, 'grown'), existing: first.journal,
        inspect: async context => { queries++; return turn(context); } });
    assert.equal(queries, 1);
    assert.equal(grown.turns.length, 3);
    assert.equal(grown.turns[0].free.contextKey, 'grown-0');
    const resumed = await collectStockfishReview({ ...defaults(), existing: saved[0] });
    assert.equal(resumed.turns.length, 2);
});

test('Final aggregation refuses incomplete, modified and foreign-source journals without searching', async () => {
    const saved = []; const full = await collectStockfishReview({ ...defaults(), checkpointed: value => saved.push(value) });
    const noSearch = () => { throw new Error('Aggregation must not search'); };
    const ready = await collectStockfishReview({ ...defaults(), existing: full.journal, requireComplete: true, inspect: noSearch });
    assert.equal(ready.turns.length, 2);
    await assert.rejects(collectStockfishReview({ ...defaults(), existing: saved[0], requireComplete: true, inspect: noSearch }), /Incomplete/);
    await assert.rejects(collectStockfishReview({ ...defaults(), existing: full.journal.replace('pinned-source', 'other-source') }), /modified/);
    await assert.rejects(collectStockfishReview({ ...defaults(), source: 'another-build', existing: full.journal }), /Foreign/);
});

test('A changed history cannot reuse a former branch even at the same board placement', async () => {
    const first = await collectStockfishReview(defaults()); let queries = 0;
    const changed = contexts(2); changed[1].moves = ['different-prefix'];
    await collectStockfishReview({ ...defaults(), contexts: changed, existing: first.journal,
        inspect: async context => { queries++; return turn(context); } });
    assert.equal(queries, 1);
    await assert.rejects(collectStockfishReview({ ...defaults(), contexts: changed, existing: first.journal, requireComplete: true }), /Incomplete/);
});

test('Storage failure and invalid native turn evidence cannot publish completed collection', async () => {
    let queries = 0;
    await assert.rejects(collectStockfishReview({ ...defaults(), inspect: async context => { queries++; return turn(context); },
        checkpointed: () => { throw new Error('disk full'); } }), /disk full/);
    assert.equal(queries, 1);
    await assert.rejects(collectStockfishReview({ ...defaults(), validate: () => false }), /Invalid Stockfish turn/);
});
