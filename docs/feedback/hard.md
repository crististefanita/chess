# Hard feedback · experimental core

## Priorities

The current task defines requirements and controlled offline improvement. Another AI owns APK/RAG delivery. Stockfish Lite owns the requested product coaching; the Java opponent must improve on its own measurements. [A/B and Elo protocol](../testing/hard-ab-elo.md) · [Coaching requirements](stockfish-web.md)

| Priority | Case | Required evidence |
|---|---|---|
| P0 | Freeze the real parent | Read live source/YAML and the actual public selector. Preserve parallel opening changes. Keep source/build/policy digests and one actual decision/report per move. No inherited experimental-Master or historical Elo. |
| P0 | Preserve the historical audit contract | `CoachingProofTest#retainsActualReserveWorkAndHistoricalReceiptShapes` exposed four new null BotDecision metadata fields changing an old digest. Port/review the prototype's optional-metadata fix and its new round-trip component; preserve the original fixture/digest and every real field of new observations. Do not certify a league on a broken archive contract. |
| P1 | A: surgical speed refactor | One optimization at a time: append/allocation, king-ray fast path or bit representation. Preserve legal-move order, perft, decisions, depth and counters. Four fresh serial JVM forks; allocation savings alone do not qualify faster execution. |
| P1 | B: one persistent losing mechanism | Reproduce the later Larsen `f2f3` versus `Nd3` with free opposing replies at a common horizon. Determine whether bounds, coverage or evaluation first diverges. Add a failing cause-level component, retain counterexamples, then freeze new related families before teacher scoring. |
| P1 | Complete auditable games | Retain Native decision plus Stockfish hint at every White/Black pre-move context, the actual played move and the complete final review. Cluster first verified WDL losses. An unaudited prefix cannot be retrospectively renamed observed. |
| P1 | Independent transfer, then measured Delta Elo | Separate exact puzzle keys, complete lines, alternative defenses and full conversion. After correctness, use fresh paired games; quality-failed diagnostic games remain non-promotable. Freeze 100 new color-reversed starting pairs and report candidate-parent Delta Elo with pair-aware 95% CI and W/D/L/unfinished. |
| P2 | Adaptive budget and reusable cache | Test fixed and adaptive policies as separate arms. Charge all resumed/forcing/confirmation work; freeze danger/instability triggers before holdout. Preserve full-history/cache-bound semantics, cold/warm parity and Basic/Advanced behavior. Do not revive a fixed 44k ceiling as an unexplained constraint. |
| P2 | Reusable puzzle lessons | General preconditions, free best defense, tempo/geometry counterexamples and excluded-family transfer. Group transpositions and mirrored positions in one partition. Stored-FEN memorization is not an engine-strength gain. |

Evidence root: `C:\work\sah`. The two-child diagnostic in `.studii/main-variants/RESULTS.md` used parent `f47c9b2`, before live's later opening change. The speed arm reduced measured allocation about 11.4%, with inconsistent CPU/wall improvements. The checked-frontier arm retained all six screened moves while spending more work. The later Larsen choice lost 465/403.5 per-mille WDL at Stockfish H17/H19. These consumed controls motivate investigation; they establish neither a current holdout nor a new Elo.

Do not launch another large unchanged-live calibration merely to report another rating. There is no new measured candidate-parent Delta Elo in this review. A6's earlier high-budget experiments and repaired study cases below remain reference evidence, not the default next campaign. [Required result table and interpretation](../testing/hard-ab-elo.md#auditable-result-and-decision)

The root/frontier defect in `training-078` is now protected by cause-level components and the actual named-policy gate: 11/11 accepted, no failed roots and no numeric override. Stockfish H17/H19 found zero White WDL loss on all seven White decisions of the supplied PV. The durable mechanism, scope and receipts belong in the [measured-repair contract](../board/hard-candidates.md#measured-repair-of-training-078), rather than a completed ToDo row.

The configured-play continuation probe separately preserves `Rf1` after `…Kf8` and `Re8` after `…Kh8`, both at completed H7 (2/2 contexts). The earlier frozen confirmation has no qualified Delta Elo and is not the default next campaign. A new named candidate needs its own raw paired replay before reporting Delta Elo or a 95% interval. See the [candidate reference](../board/hard-candidates.md#reproducible-confirmation) and the current protocol above. The retained Native rows remain available as diagnostic witnesses.

The public selector remains `LEGACY`. No product promotion or new ΔElo is claimed by these tests. Do not assign native Elo or another evaluator/budget's results to the experimental engine. Do not add an ID/FEN lookup to pass a test. Preserve counterexamples and useful FAIL reports; the separate native feedback register remains open.

[Core contract and verification](../board/hard-candidates.md)
