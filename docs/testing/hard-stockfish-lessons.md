# What Native Hard can learn from Stockfish Lite

[Current selective repair](hard-selective-repair.md) · [Open priorities](../feedback/hard.md) · [A/B protocol](hard-ab-elo.md)

## Identify the actual reference

J7's coaching package is Stockfish.js 19.0.1, source `0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6`, with the smaller Lite NNUE network. Lite changes the evaluation/network footprint; it does not turn the engine into a basic material counter. The maintainer documents its strength/size tradeoff in the [pinned upstream README](https://github.com/nmrugg/stockfish.js/blob/0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6/README.md). J7's precise provider, build and options remain in the [integration contract](../../web/stockfish.md).

Stockfish combines trained NNUE evaluation, incremental updates, ordered alpha-beta/PVS search and selective tactical work. Its [evaluation documentation](https://official-stockfish.github.io/docs/stockfish-wiki/Advanced-topics.html#classical-versus-nnue-evaluation) explains training on millions of evaluated positions; its [NNUE implementation guide](https://official-stockfish.github.io/docs/nnue-pytorch-wiki/docs/nnue.html) describes features, accumulators and quantized inference. This suggests that adding budget alone cannot replace a better evaluator. It does not quantify which mechanism explains J7's gap: that requires controlled ablation.

## Ordered experiments

| Priority | Isolated Native child | Component admission | Measured benefit |
|---|---|---|---|
| First | Replace unnecessarily exact selected-root comparisons with the sufficient bound question, without changing the current frozen veto copy | Small-tree minimax oracle; full versus null-window; reversed root order; cold/warm cache; interrupted work never exact. A strict parent win must exclude a tie, with both roots at the same horizon. | Retain 056/091/096, all eight first keys and complete-route gains; reduce charged confirmation work. Then source-held-out decisions and paired games. |
| Second | Reuse ordered search work with explicit context and typed bounds | Distinguish EXACT/LOWER/UPPER, remaining horizon and static versus forcing leaf mode. Preserve full history, rights, clocks, repetition and accounting. Compare enabled/disabled and no-hash references. | Same accepted decision quality with fewer generated nodes, or stronger play at the same declared allowance; report both separately. |
| Later | Train a small Native evaluation model from verified teacher labels | Deduplicate by source-game/position family before splitting. Keep evaluation sources withheld, record model/features/weights and a classical baseline. Never import a puzzle answer or live coaching response as the Native move. | Held-out WDL loss and severe blunders, whole-line conversion and a separately declared paired Delta Elo. Training accuracy and remembered catalog keys do not establish strength. |

The implementation references are Stockfish 19's [search and quiescence](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp), [transposition types](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/tt.h) and [move ordering](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/movepick.cpp). They are reading targets, not evidence that their constants, reductions or pruning are safe in J7. Quiescence forbids stand pat while in check; a bounded answer is not an exact score. Preserve sound sacrifices and legal quiet evasions. Do not repeat the rejected forcing-everywhere probe under a new name.

## Preserve causality and the running match

Keep the corroborated-veto candidate, its compiled inputs, source partition, node accounting and fixed 100-pair match unchanged. New experiments get separate recipes and receipts. Start with the bound-only child after this measurement; do not compound new evaluation, ordering and budget changes into one result. The [Fishtest contribution guidance](https://official-stockfish.github.io/docs/fishtest-wiki/Creating-my-first-test.html#contributor-guidelines) explicitly favors small changes and one idea per test.

The full catalog is now consumed regression evidence. Demand new cases with actual mechanism activity, free adversarial replies and complete results. A longer PV, a smaller binary or an equal node count across different engines is not a strength comparison. Report node work, elapsed cost and playing outcome separately. Confidence intervals and unfinished-game sensitivity remain mandatory; no puzzle gain inherits the old absolute Elo anchor.

This is a research roadmap. No new model, Stockfish-derived Native search, strength gain or engine promotion is claimed by writing it. Coaching ownership stays with Lite; Basic, Advanced and Hard remain independent Native opponents.
