# Native Hard progress: steps and decisions

## Overview

[Open feedback](../feedback/hard.md) · [Measurement protocol](hard-ab-elo.md) · [Stockfish Lite ownership](../feedback/stockfish-web.md)

The [current calibration](current-profile-calibration.md) measures all three profiles and authored puzzle routes, with original-prefix technical continuations and explicit quality failures. Use its scope before interpreting a newer Elo point as an algorithmic gain.

The [continuation copies](hard-continuation-copies.md) qualify the next intervention: preserve selective replay's recoveries, repair three genuine regressions and correct the source/goal mismatch in training-060. A benchmark reference must be independently validated before it becomes a learning target.

Treat root correctness and the explanation's continuation as separate gates. The [checked-frontier study](native-forcing-frontier.md) captures a winning Native first move followed by an unclosed queen offer; a first-choice PASS alone cannot close that mechanism. Preserve the stored counterexample without requiring future engines to repeat its bad PV.

## Working baseline

Start from current main and identify every experimental copy. A3 changes only exact-key construction from board occupancy; it changes no budget, repertoire, evaluation or move. Speed confirmation and playing-strength improvement are separate outcomes. [Initial A3 qualification and rejected experiments](hard-current-variants.md)

## Execution order

1. **Close audit defects.** An old receipt must retain its original shape and digest. Omit newly introduced fields only when null; preserve and verify actual new data. Reproduce, repair, and retain both Extent results. Keep the original fixture.
2. **Confirm optimization before integration.** A3 preserved correctness but failed dense-context confirmation, so it remains experimental. Use dense and sparse controls, alternating order and fresh serial JVMs for the next candidate. Preserve ordered moves, PVs, depths and reported nodes. A large Elo league is unnecessary to prove an exact refactor; a favorable mean for one position type does not establish general speed.
3. **Locate the first cause of a playing failure.** In Larsen, isolated H6 comparisons and the actual decision are different measurements. Record complete/incomplete iterations, per-root cost, score type, free reply and stopping reason. A better score in a forced pair does not prove that a complete decision can adopt it.
4. **Test one general mechanism in a strength candidate.** Cover the cause and counterexamples with components, then excluded families and conversion. Preserve justified vetoes until contextual evidence can replace them. PVS already exists; an active option is not a new improvement.
5. **Train and verify with games and puzzles.** Turn each game's first confirmed WDL decline into a study position with defenses and counterexamples. Keep the source game, transpositions and reflections in one partition. YAML records the lesson and general conditions, not a FEN-to-move override credited as learning.
6. **Measure Delta Elo.** A legal candidate may receive a diagnostic pilot before promotion; tactical FAIL remains visible. Promotion requires the agreed gates and confirmation on 100 new pairs. Retain pair-aware 95% CI, W/D/L and unfinished games. No new completed games means `NOT_MEASURED`, never an invented zero Elo.

After three failed mechanisms, revisit the cause and loss family instead of renaming the same ordering variation. Budget adjustment is separate from algorithm change. Budgets are configurable; do not reduce depth to improve a timing claim.

## The two directions after A3

| Candidate | Objective | Evidence before promotion |
|---|---|---|
| Speed | Reprofile A3; optimize one actual cost | Oracle, parity and measured CPU. Hypotheses include incremental keys, position copies and generation allocations; they are not promises. |
| Strength | Repair the confirmed mechanism in a complete decision | Correct minimax and bounds, free replies, controls, transfer and complete results, then its own Delta Elo. |

Compare each candidate against the same baseline. Combining them creates another identified arm; do not attribute extra-budget improvement to an algorithm or change repertoire silently.

## Coach and learning journal

Stockfish Lite owns the target for calculated hints and evaluations in AI games, manual/local analysis, openings, puzzle explanations, the bar and final review on Web/APK. The Native opponent is separate. Platform integration and gaps belong to [their feedback](../feedback/stockfish-web.md), while APK/RAG belongs to the delivery owner.

Before every actual White/Black move, retain the actual Native observation and pre-move Lite hint with complete history and engine identity. Retain the complete final analysis. The separate offline oracle compares free and played choices at a common horizon; it neither replaces Hard's decision nor reconstructs missing observations retrospectively.

For a composed position, preserve its initial FEN and subsequent moves explicitly. An artificial game checkpoint alone does not prove origin. Verify the restored Native position before oracle scoring and reject mismatched origins. The first `training-012` probe in this round was invalidated by this control and rerun with explicit origin; the invalid attempt is not a playing result.

## Clean feedback and evidence

Feedback keeps current gaps and closure criteria. After verification, move durable behavior and lessons to the owning technical guide, retain the evidence link and remove the completed ToDo. A rejected experiment remains documented without dominating the next round.

Keep builds and evidence externally under `C:\work\sah`, with paths relative to that root. Each study retains an index, Extent with original assertions, necessary PGN/journal data and only logs that add information absent from the report. An imported report is not a new test execution.
