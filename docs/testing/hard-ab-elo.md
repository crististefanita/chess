# Native Hard: controlled improvement and Elo

## Purpose and ownership

Improve the Java opponent using reproducible evidence. Stockfish Lite owns product hints, the evaluation bar and reviews; it is a teacher observation, not a replacement move generator for Native Hard. The Windows Stockfish executable can be a separate offline referee. Never assign its rating or a longer PV to the Java opponent. [Coaching contract](../feedback/stockfish-web.md) · [Current priorities](../feedback/hard.md)

This protocol is version-independent. Store experiments under `C:\work\sah`, with evidence paths relative to that root. The APK, RAG and publication work belong to the separate delivery owner; documentation and offline comparisons do not qualify an APK.

## Freeze the real parent

Record the immutable source commit, working-tree diff, source/build hashes, Java version, actual public selector, YAML/repertoire digests, node-accounting definition and starting-position partition before evaluation. Read the configured opponent rather than assuming that a class named Master or Next is live. Opening changes require a fresh baseline; an earlier Elo does not certify a new book policy.

Call `analyzeBot` once per Native decision and retain that decision's own report. Computing another search only to attach a longer PV changes the observation. A move actually played must match the audited Native choice; the accompanying Stockfish hint must not silently become the opponent.

| Arm | Changes | Primary acceptance |
|---|---|---|
| Parent | Frozen current public Native opponent | Reproducible source, policy, moves and accounting. |
| A: speed | One representation/allocation/bit-operation optimization | Identical ordered legal moves, perft, decisions, completed depths and same-work counters. Measure allocation, process/thread CPU and wall time in fresh serial JVM forks. |
| B: strength | One general search or evaluation mechanism | Legal minimax/bound behavior, critical counterexamples, independent-family transfer and full-game conversion, followed by its own paired match. |
| Budget study | Fixed and adaptive resource policies | Report separately from algorithm changes. Freeze the adaptive trigger before holdout evaluation; charge confirmation, forcing and resumed work. |

A strength test must not quietly incorporate A, a new book, a new budget and a new evaluator at once. After individual qualification, their combination is another explicitly named arm.

## Components before large campaigns

- Compare exact scores and bound types against exhaustive minimax on small legal positions. Cover EXACT/LOWER/UPPER entries, interrupted iterations, cold/warm/disabled cache, repetitions, move clocks, en passant, castling, mate distance and promotion.
- A checked leaf cannot stand pat. An unfinished capture/check continuation is not an exact final score. Preserve valid sacrifices and counterexamples where a capture is unsound; do not encode a FEN-to-solution override.
- For speed work, compare against an independent legal-move oracle and perft. Include pinned pieces, discovered attacks, king captures, en passant and promotion order. Equal node counts alone do not establish equal search semantics.
- For N→N+1 reuse, retain full history, turn, rights, clocks, policy and remaining depth. Revalidate cached bounds in their current window. A persisted lesson or cached result is not evidence of generalization.
- Keep Basic and Advanced unchanged unless that scope is explicitly authorized. Refactoring an experimental candidate must not silently select it as the public opponent.

Use at least four independent warmed JVM forks for an initial speed comparison, alternating arm order. Report every fork and the work actually executed. Allocation savings with inconsistent CPU/wall results remain unqualified as a speed claim. At equal nodes, exact refactoring should preserve strength; possible strength gains from spending saved time require a separate equal-time experiment.

## Puzzle and game evidence

Partition by source game and position family, keeping transpositions and color-reversed equivalents together. Freeze study, validation and final holdout membership before teacher scoring or tuning. A consumed failure becomes a regression control, not another fresh holdout.

For puzzles report first move, complete line, free alternative defenses and eventual conversion separately. Use Syzygy where applicable, retaining side, rights, clocks and the applicable draw rules. YAML lessons need general preconditions, a best defense, a counterexample and an excluded-family transfer check. Exact stored solutions may serve the educational catalog, but cannot count as a search improvement.

For every actual White and Black ply in an audited game retain:

1. Run/game/ply identity, full prefix, FEN, actor and actual Native decision/report with source, policy, depth and measured work.
2. Stockfish's hint for that same pre-move context, original UCI, provider/build/network identity, options, actual nodes, completed depth, score, WDL and legal PVs.
3. The played-root and free-root comparison from the same referee at a common completed depth. Keep raw CP/mate when WDL saturates. Do not compare Native centipawns directly with Stockfish centipawns.
4. The actual executed move, followed by the complete final review linked to the same game history. Missing work remains missing; never reconstruct an unaudited game and call its earlier hints observed.

For one referee and perspective, WDL loss is `max(0, E(best) - E(played))`, where `E = W + D/2` in per-mille units. This is expected score, not the probability that a particular human will win. PV length, completed depth and selective depth are separate measurements. [Stockfish's UCI definitions](https://official-stockfish.github.io/docs/stockfish-wiki/UCI-Protocol-and-Stockfish-Commands.html)

Cluster first significant losses by mechanism and cumulative verified WDL loss. Fix the highest-impact reproducible family rather than rotating through named puzzles indefinitely. After three failed mechanism experiments, review the hypothesis and evidence before starting another near-identical run; do not loosen the guards.

## Separate correctness, diagnosis and promotion

Illegal moves, invalid draw/mate handling, corrupt bounds or false receipts block match interpretation. A legal candidate that fails a tactical quality guard may still enter a clearly marked **diagnostic, non-promotable** match to measure tradeoffs. Such a match does not repair the failed gate, relax its threshold or supply a qualified release rating.

Promotion requires the existing critical guards, accepted-stage regression, independent transfer and full-game conversion. Then compare the candidate directly with the frozen parent; recalibrating unchanged live code against an anchor alone does not measure candidate improvement.

## Paired games and Elo

- Freeze 100 genuinely new starts/prefixes for an initial candidate–parent comparison: two color-reversed games per start, 200 games total. Preserve complete PGNs, engine options, exits and the result of every game.
- Estimate uncertainty with the starting pair as the independent cluster, or use a documented pentanomial/SPRT model. Moves and color-mirrored puzzle cases are not independent match samples.
- Predeclare the confirmation batch and stopping rule. If uncertainty is too wide, extend with new pairs under that rule; do not keep checking until a positive interval appears. A larger confirmation can use 200–1,000 pairs according to the intended effect and resources.
- Report W/D/L and unfinished games separately. Finish capped games where practical. Otherwise report sensitivity to all unfinished games being wins or losses; do not silently count them as draws in a promoted rating.
- For resolved games, `p = (W + D/2)/(W + D + L)` and `Delta Elo = 400 log10(p/(1-p))`. Boundary scores have unbounded estimates. Pair-aware confidence intervals and the censored-game policy are required beside the point estimate.
- Stockfish configured at `UCI_Elo` 1500/1600/1700 supplies protocol-specific external anchors. Record executable, network, strength options, time/nodes and hardware. The setting is not a certified human rating under our different control. Report relative estimates and intervals; do not average or inherit incompatible campaigns.

Keep equal-node, adaptive-budget and equal-time results in different rows. Native `LEGACY_EXTRA` counts omit common work and cannot be reported as total nodes or used to invent total NPS. Comparison across different engines at equal requested nodes also does not mean equal computation per node. [Fishtest statistical methods](https://official-stockfish.github.io/docs/fishtest-wiki/Fishtest-Mathematics.html)

## Auditable result and decision

Use ATF reporting/Extent for component and platform suites. Preserve original expected/actual/verdict rows, source/run identity, raw-engine outcomes and any captures needed for the claim. Reject missing assertions, changed PNGs, mismatched histories and foreign runs. A mock proves its contract, not Android execution.

Govern the rating result itself with components: known W/D/L fixtures and boundary scores; paired interval/seed reproducibility; mirrored starts kept in one cluster; missing and duplicate games; changed PGN/result; foreign engine/source/options; unfinished-game policy; and a point estimate without its interval. Zero completed games must produce `NOT_MEASURED`, not zero Delta Elo or a retained rating relabelled as new.

The minimum useful study is an index, self-contained Extent, complete PGNs and the per-move/final-review journal needed for replay. Keep additional logs only for unresolved failures or independent verification. Do not scatter duplicated captures and summaries into the project.

Every iteration ends with: parent/candidate IDs; changed mechanism; tests and critical regressions; fresh transfer; conversion/defense; W/D/L/unfinished; candidate–parent Delta Elo with 95% interval or `NOT_MEASURED`; actual resource costs; and an explicit promote/reject/inconclusive decision. An unchanged choice on a small screen is neither a measured zero Delta Elo nor proof of improvement.
