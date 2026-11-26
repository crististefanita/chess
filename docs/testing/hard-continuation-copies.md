# Hard continuation copies: measured progress and failed gates

[Hard priorities](../feedback/hard.md) · [Current profile calibration](current-profile-calibration.md) · [A/B protocol](hard-ab-elo.md)

## Overview

Six copies, including the baseline, start from main `cda04af9388707b3533d5cb487e5f2ccbfa8e5f5`. The discovery set is the **26 consumed material exercises** where the baseline accepted the first key but later departed from the authored route. It is not a fresh holdout. Each copy also plays all 1,238 authored routes without receiving the solution before its actual decision.

No experimental opponent is promoted. First-move agreement, complete authored route, independently estimated WDL, conversion to a game result and paired Delta Elo are different outcomes. Preserving a source string does not establish good chess; neither does improving ten selected strings establish general strength.

## Separate interventions

| Copy | Change from main | Recovered target stages / 26 | First keys / 1,238 | Complete routes / 1,238 | Full routes gained / lost |
|---|---|---:|---:|---:|---|
| Baseline | Existing adaptive 100k–200k, ordinary H6 | 0 | 1,214 | 1,188 | 0 / 0 |
| Budget | Adaptive 200k–400k, ordinary H6 | 0 | 1,212 | 1,186 | 0 / 2 |
| H8 | Same 200k–400k, ordinary H8 | 10 | 1,218 | 1,188 | 12 / 12 |
| Frontier reuse | Baseline budget; activate existing exact/bound/preferred-move reuse from the first iteration | 0 | 1,213 | 1,187 | 0 / 1 |
| H7 | Same 200k–400k, ordinary H7 | 7 | 1,212 | 1,180 | 8 / 16 |
| Selective replay | Retain the original parent search and H6 gate. Recheck nonchecking parent leaders with deeper search; charge both searches to a total 500k allowance | 6 | 1,222 | 1,190 | 8 / 6 |

All copies preserve Basic and Advanced exactly on the 26 contexts: 312 profile comparisons across the six arms, including ordered PVs, depth and charged work. The public node counter remains **extra work excluding shared H3**. The transposition ceiling stays 50k. A budget increase is not an algorithmic improvement.

The H8 copy explicitly extends the parser's existing H7 limit only in that child. An initial unqualified preflight and a Java constant-inlining mistake are retained separately; actual loaded policies are checked before the common freeze. H7 and selective replay are subsequent discovery ablations, separately frozen and bound to that parent. They are not independent confirmation sets.

Changing the configured terminal horizon also changes where the existing terminal-leader veto applies. Several H8 recoveries already finish at H6/H7. Do not attribute their gains solely to reaching H8. Selective replay investigates a separate safety rule; it preserves checking parent leaders, which also means it cannot repair some bad checking recommendations.

## Stockfish comparison and clock correction

The pinned unrestricted desktop Stockfish 19 compares free play, the actual Native choices and the authored reference at completed H17/H19. Each phase is cold, one thread, 32 MiB hash, with a declared ten-million-node safety cap. An unfinished horizon cannot supply a final score. The Lite product coach is a different provider.

Six initial comparisons used the catalog's nonzero origin clock while the Native composed match had reset its quiet clock. Those groups are superseded and retained. Corrected phases use the actual Native history and clock; components reject reusing the uncorrected origin. Across the study, **256 raw teacher phases** remain available, including the superseded probes. The qualified target comparisons use 172 phases; the separate regression investigation uses 48. The portable target fixture retains the 156 free/baseline/reference phases required for its 26 cases.

| Copy | Large estimated WDL losses at both horizons / 26 | Mean H19 loss, per-mille | Mean actual extra nodes |
|---|---:|---:|---:|
| Baseline | 16 | 495.85 | 79,768 |
| Budget | 16 | 495.85 | 104,483 |
| H8 | 8 | 210.54 | 229,880 |
| Frontier reuse | 16 | 495.85 | 59,312 |
| H7 | 11 | 322.83 | 173,091 |
| Selective replay | 12 | 361.88 | 302,160 |

“Large” means at least 150 per-mille loss at both completed horizons. These are Stockfish estimates on consumed positions, not tablebase proofs or converted games. The H8 arm has a real local signal, but loses protected controls and twelve complete routes elsewhere. The frontier arm's lower work does not qualify an optimization while it loses the rook invasion. Concurrent timings do not establish controlled serial performance.

## Genuine regression versus curriculum mismatch

The selective arm passes the twelve existing component methods, retains every accepted first key and gains eight new keys. Its six strict full-route losses have different meanings:

| Case | Independent H17/H19 result | Consequence |
|---|---|---|
| training-056 | Candidate Rxc7 estimates a draw; the parent Nxc7 retains the win | Genuine regression; protect the actual accepted continuation in components. |
| training-091 | Candidate Bxh2+ loses at least 520 per-mille; Rf8 retains the win | Genuine regression; inspect the free defense and completed frontier. |
| training-096 | Candidate Nd5 estimates a loss; Qxh4 retains the win | Genuine regression; the local target gains cannot erase it. |
| tactic-003, training-040 | Different authored move, same winning WDL estimate at both horizons | Strict-route mismatch, not demonstrated playing degradation. Conversion and an explicitly curated alternate route are still needed. |
| training-060 | The old Kxg2 reference estimates a loss; candidate e4 retains the win | A corrupted reference cannot become a golden answer. |

The [original Lichess 0470w response](https://lichess.org/api/puzzle/0470w) confirms `Kxf2 b5 e4 b6 e3 b7 e2`. Independent chess.js replay of its 71-ply source PGN reproduces the exact catalog FEN. The project had replaced the source continuation with greedy Kxg2. The raw source and independent replay are retained in test resources.

Do not simply substitute a seven-ply line and weaken the existing closed +200 material requirement: this source is a promotion race, and the visible promotions temporarily reverse material balance. Curation must verify an objective-compatible complete payoff or explicitly support a position-winning objective, preserving the ID, saved progress, hints and final explanation. Until then, exclude the faulty Kxg2 reference from strength conclusions. The original catalog remains unchanged in this study; its published curation defect is an explicit failed quality goal.

`HardLateContinuationTest` isolates all 26 stored contexts and rejects changed run, stage, clock, WDL and safety cap. `NativeContinuationRouteTest` makes actual Native calls for the three independently sound route guards. `MaterialRegressionTest` distinguishes the three real regressions, the two healthy alternatives and the losing reference; it also replays the primary source and verifies both protected rook invasions. No test requires a future engine to repeat a stored bad choice. These fixtures are test-only knowledge, not runtime FEN-to-move overrides.

## Next intervention and promotion

The [selective repair follow-up](hard-selective-repair.md) has now repaired the three continuations in an external corroborated-veto copy while keeping all eight first-key recoveries and improving complete routes to 1,193. It preserves the original study's failed verdicts; the new copy's transfer and paired Elo must be assessed separately.

Keep selective replay as the most useful **unqualified integration candidate**: +8 first keys and +2 complete authored routes, but three confirmed playing regressions. Isolate the first wrong evaluation in training-056/091/096 with free replies at a common horizon before adding another global budget or another remembered position. Preserve its existing recoveries and the original rook/queen controls. Treat the checking-parent bypass as a limited hypothesis, not a soundness theorem.

Separately repair the training-060 reference/goal mismatch from the primary source. Audit a sample of other material references before admitting them as learning targets. Pattern YAML must include prerequisites, the best defense, counterexamples, source identity and independent transfer; matching a frozen exercise is not autonomous learning.

All five experimental promotion gates fail. No fresh-family conversion campaign or paired match was started: **Delta Elo = NOT_MEASURED**. Once a source-identified candidate clears the genuine regressions and the catalog admission problem is resolved, freeze excluded source games/families and run its own paired comparison. The previous three-profile Elo table remains a baseline, not an Elo value inherited by any child.

The experiment design follows the distinction between exact/bounded search information in [Stockfish's search implementation](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp) and paired testing in [Fishtest's statistical methodology](https://official-stockfish.github.io/docs/fishtest-wiki/Fishtest-Mathematics.html). These references justify testing mechanisms and preserving pair dependence; they do not certify the local implementation or predict its gain.

Evidence root: `C:\work\sah`; current relative collection `.studii/hard-continuations/`. Its README indexes the original ATF Extent reports, frozen copies, Native ledgers, corrected teacher phases, regression classification and explicitly blocked promotion. No Android, browser, APK or release qualification is claimed.
