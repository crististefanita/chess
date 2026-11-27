# What Native Hard can learn from Stockfish Lite

[Current selective repair](hard-selective-repair.md) · [Open priorities](../feedback/hard.md) · [A/B protocol](hard-ab-elo.md)

## Identify the actual reference

J7's coaching package is Stockfish.js 19.0.1, source `0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6`, with the smaller Lite NNUE network. Lite changes the evaluation/network footprint; it does not turn the engine into a basic material counter. The maintainer documents its strength/size tradeoff in the [pinned upstream README](https://github.com/nmrugg/stockfish.js/blob/0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6/README.md). J7's precise provider, build and options remain in the [integration contract](../../web/stockfish.md).

Do not confuse that coach with the [rating anchor](current-profile-calibration.md). Coaching requests use 200k nodes without skill limiting; the desktop calibration anchor requests 6k with `UCI_LimitStrength=true` and `UCI_Elo=1500`. They are different experiments and configurations. Beating the weakened anchor cannot imply parity with unrestricted Lite. [Upstream strength-limiting options](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html).

Stockfish combines trained NNUE evaluation, incremental updates, ordered alpha-beta/PVS search and selective tactical work. Its [evaluation documentation](https://official-stockfish.github.io/docs/stockfish-wiki/Advanced-topics.html#classical-versus-nnue-evaluation) explains training on millions of evaluated positions; its [NNUE implementation guide](https://official-stockfish.github.io/docs/nnue-pytorch-wiki/docs/nnue.html) describes features, accumulators and quantized inference. This suggests that adding budget alone cannot replace a better evaluator. It does not quantify which mechanism explains J7's gap: that requires controlled ablation.

## Ordered experiments

| Priority | Isolated Native child | Component admission | Measured benefit |
|---|---|---|---|
| First | Replace unnecessarily exact selected-root comparisons with the sufficient bound question, without changing the current frozen veto copy | Small-tree minimax oracle; full versus null-window; reversed root order; cold/warm cache; interrupted work never exact. A strict parent win must exclude a tie, with both roots at the same horizon. | Retain 056/091/096, all eight first keys and complete-route gains; reduce charged confirmation work. Then source-held-out decisions and paired games. |
| Second | Reuse ordered search work with explicit context and typed bounds | Distinguish EXACT/LOWER/UPPER, remaining horizon and static versus forcing leaf mode. Preserve full history, rights, clocks, repetition and accounting. Compare enabled/disabled and no-hash references. | Same accepted decision quality with fewer generated nodes, or stronger play at the same declared allowance; report both separately. |
| Later | Train a small Native evaluation model from verified teacher labels | Deduplicate by source-game/position family before splitting. Keep evaluation sources withheld, record model/features/weights and a classical baseline. Never import a puzzle answer or live coaching response as the Native move. | Held-out WDL loss and severe blunders, whole-line conversion and a separately declared paired Delta Elo. Training accuracy and remembered catalog keys do not establish strength. |

The implementation references are Stockfish 19's [search and quiescence](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp), [transposition types](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/tt.h) and [move ordering](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/movepick.cpp). They are reading targets, not evidence that their constants, reductions or pruning are safe in J7. Quiescence forbids stand pat while in check; a bounded answer is not an exact score. Preserve sound sacrifices and legal quiet evasions. Do not repeat the rejected forcing-everywhere probe under a new name.

The first baseline already has a component oracle: [NativeMinimaxWindowTest](../../atf-tests/src/test/java/ro/atf/chess/NativeMinimaxWindowTest.java) independently enumerates every legal branch in small H1-H3 trees, using the declared shared leaf evaluator. Full PVS/alpha-beta, null-window boundaries, strict wins versus ties and warmed full searches must agree with it. The separate retained H5 witnesses protect the actual three continuation contexts. These checks do not establish a trained evaluator or an Elo improvement.

## First bound-only prototype

An independently frozen external `bound` copy now asks the integer-score question `[parent-1,parent]` instead of always computing a second exact score. The parent remains exact; challenger evidence is explicitly EXACT, UPPER, LOWER or INCOMPLETE. Only a completed exact/upper value strictly below the parent can veto. Bounds never replace an ordinary final score or PV. The original horizons, 500k nomination and 3M total ceiling stay unchanged.

The consumed 17-case focus retains **17 first keys, 13 complete routes, all eight recovered keys and 056/091/096**. All **53 matched contexts choose the same root**, with **30,363,333 → 27,850,335** charged extra nodes, an **8.28% reduction**. The completed **1,238-route catalog** retains **1,222 first keys and 1,193 routes**, with no first-key/route losses versus veto. All **2,219 matched contexts choose the same root**; their work falls **154,582,272 → 150,499,363**, or **2.64%**. Do not substitute the focused saving for the whole-catalog result.

Components pass **27/27**, including typed-bound negatives, interruptions, minimax and the actual protected continuations. Basic and Advanced retain identical full observations on **26 contexts / 52 actual calls**. These are overlapping correctness/diagnostic checks, not unique aggregate coverage, fresh transfer, serial speed or Elo. Mechanism-active source transfer and this child's own playing qualification remain separate gates.

Evidence root `C:\work\sah`, collection `.studii/selective-repair/`: `bound-extension.json`, `output/bound-grade.json`, `output/bound-catalog-grade.json`, `output/bound-regression.html`. Original logs, snapshots and receipts are retained. A three-method rerun corrects inherited helper labels so a completed bound question is not described as two exact scores. The ongoing 100-pair campaign still uses the earlier veto child.

A later evaluation experiment may compare a trained Native model with an attributed, compatible small pretrained NNUE evaluator. Keep Native search independent of live teacher moves. Pin network/features, verify incremental updates against complete rebuilding, and govern score units, perspective and mate ranges before playing. Unknown pretraining data cannot be called a source-clean unseen test set merely because local YAML examples were excluded.

## Preserve causality and the running match

Keep the corroborated-veto candidate, its compiled inputs, source partition, node accounting and fixed 100-pair match unchanged. New experiments get separate recipes and receipts. The independently frozen bound prototype may test arithmetic and consumed regressions alongside the unchanged match; its own strength qualification cannot inherit the running candidate's result. Do not compound new evaluation, ordering and budget changes into one result. The [Fishtest contribution guidance](https://official-stockfish.github.io/docs/fishtest-wiki/Creating-my-first-test.html#contributor-guidelines) explicitly favors small changes and one idea per test.

The full catalog is now consumed regression evidence. Demand new cases with actual mechanism activity, free adversarial replies and complete results. A longer PV, a smaller binary or an equal node count across different engines is not a strength comparison. Report node work, elapsed cost and playing outcome separately. Confidence intervals and unfinished-game sensitivity remain mandatory; no puzzle gain inherits the old absolute Elo anchor.

This is a research roadmap. No new model, Stockfish-derived Native search, strength gain or engine promotion is claimed by writing it. Coaching ownership stays with Lite; Basic, Advanced and Hard remain independent Native opponents.
