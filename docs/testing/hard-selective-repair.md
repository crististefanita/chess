# Selective Hard: corroborated continuation veto

[Open priorities](../feedback/hard.md) · [Original six-copy study](hard-continuation-copies.md) · [A/B and Elo contract](hard-ab-elo.md)

## Result and scope

The new external `veto` copy repairs training-056/091/096 while retaining all eight first-key recoveries. On all 1,238 current authored routes it reaches **1,222 first keys and 1,193 complete routes**, versus main's **1,214 / 1,188** and the original selective copy's **1,222 / 1,190**. No accepted first key is lost. The three remaining strict route losses are the previously independently classified healthy alternatives in tactic-003/training-040 and the poisoned training-060 payoff; their meanings have not changed.

These are consumed catalog results, not an Elo estimate, a proof of every free defense, or a promoted engine. The runtime selector on main remains unchanged until a separate integration decision. The fixed 100-pair candidate–main campaign is a **diagnostic, non-promoting** measurement under the existing protocol; retain its final paired confidence interval and unfinished games before any strength conclusion.

## Cause and intervention

At a common ordinary horizon, completed full windows show repeated leader inversions. In 056 and 091 the parent wins the H5 comparison, the rejected selective root wins at H6, and the parent leads again at H7. In 096 the parent leads at H5/H6, the rejected root leads at H7, and the parent leads again at H8. These are exact results for those static horizons; exactness does not imply a tactically closed frontier.

A mandatory confirmation can lose a correct selective recovery simply because additional work is interrupted. The retained mechanism therefore keeps the last **completed** selective ranking unless:

1. A full-window comparison of parent and challenger at the next horizon completes and favors the parent.
2. A separate common-horizon forcing comparison, or an adjacent ordinary comparison, corroborates that direction.

Scores from different horizons are never averaged. Each comparison uses free legal replies. Static and forcing phases have separate caches. The forcing frontier searches legal captures/promotions and all evasions while in check; it forbids stand pat in check. Its cycle, safety or node stop is incomplete and supplies no veto. It does not claim to explore arbitrary quiet moves beyond its ordinary horizon.

The unchanged nomination has a 500k extra-node allowance. The corroborated copy declares a **3M total extra-node ceiling**, including all confirmation work. The shared H3 work retains the project's existing accounting convention and is not represented as total NPS. The original parent H6 guard, checking-parent bypass and dedicated rook path remain intact. The bypass is a retained hypothesis, not a theorem of soundness.

Before production adoption, extract the nomination and confirmation settings into an explicit policy. Govern enabled/disabled behavior, remove the unused experimental forcing switch and hidden nomination constant, and preserve main's parser safety/default-policy contracts. The external parent's duplicated YAML and wider experimental parser ceiling are study scaffolding, not an approved production interface. Keep the qualified playing behavior identical through that refactoring.

## Controlled comparisons

| Copy | Observed result | Decision |
|---|---|---|
| Original selective, 500k | +8 first keys / +2 complete routes, but three real WDL regressions | Reference for preserving recoveries. |
| Budget only, 2M | Fixes 056; still loses 091 and 096 | More computation alone is insufficient. |
| Budget only, 3M | Still loses 091/096 and drops the recovered training-036 first key; focused set 16/17 keys, 11/17 routes | Reject. The equal-ceiling control does not reproduce the corroborated mechanism's 17/17 and 13/17. |
| Mandatory next-horizon pair, 2M total | Repairs all three and passes 11 critical methods, but loses required recovered keys including tactic-073 and training-110 | Failed arm stopped early; preserve completed ledger rows without claiming a full catalog. |
| Forcing pair, 2M total | Repairs 056/091, misses 096 and loses three recovered first keys on the 17-case focus | Reject. Closing captures alone does not solve the quiet continuation. |
| Forcing plus adjacent confirmation, 2M total | Repairs the three guards but loses recovered keys, including training-026/076 | Failed arm stopped early. |
| Corroborated veto, 3M total | Keeps all eight keys, repairs the three continuations; full catalog 1,222 / 1,193 | Best current experimental integration candidate; Elo and broader admission evidence remain separate. |
| Typed-bound challenger, same 3M total | Same 1,222 / 1,193 on the full catalog; all 2,219 matched contexts retain the same root, with 2.64% fewer extra nodes | Separate efficiency prototype. Focused 53-context saving is 8.28%; do not extrapolate it to general speed or inherit veto's paired Elo. [Contracts and evidence](hard-stockfish-lessons.md#first-bound-only-prototype). |

The 17-case focused comparison has 9/17 keys and 5/17 complete routes on main, 17/17 and 10/17 on original selective, and **17/17 and 13/17** on the corroborated copy. Do not report those consumed cases as independent transfer.

## Independent teacher and withheld sources

Stockfish 19 ran 18 fresh unrestricted H17/H19 phases on the three regressions, with raw UCI, exact composed history and explicitly normalized origin clocks. Each requested horizon completed. The repaired Native roots retain the teacher-confirmed wins; the old Rxc7+, Bxh2+ and Nd5 choices remain negative regression witnesses.

A separate public [Lichess puzzle batch](https://lichess.org/api) supplied 24 distinct source games after the candidate was frozen. Independent chess.js replay verified each full source PGN and solution legality. Source-game/within-batch family overlap and exact current-catalog boards were excluded before Native and teacher calls. This is a defined source-held-out sample, not a claim of exhaustive novelty across every retired archive.

All **102** H17/H19 teacher phases completed. The candidate introduced no new loss of at least 150 per-mille at both horizons. Mean H19 WDL loss was **83.44 per-mille on main** and **41.77 on both selective copies**: one corrected source position accounts for the difference. Only **one** extra confirmation activated, and its root matched original selective. This is preliminary transfer of the nomination, with too little activity to establish broad generalization of the new veto. Do not tune the engine on this sample and then keep calling it withheld.

The reused teacher driver's original completion receipt retained consumed-study boilerplate. Its raw receipt is preserved; `output/transfer-grade.json` explicitly records the corrected scope from the frozen source, actual histories, IDs and raw position commands. No phase or old verdict was relabelled as a different observation.

## Component and evidence contracts

- `NativeContinuationRouteTest` makes actual Native calls at all three retained histories; main passes, the old selective copy fails, and the new corroborated copy passes.
- `NativeSelectedWindowTest` compares full-window PVS against alpha-beta on both roots, warms the cache with fail-high and fail-low windows, reverses root order, and verifies that an empty allowance produces actual budget exhaustion. It does not freeze a bad historical evaluation as a required answer.
- `NativeMinimaxWindowTest` independently enumerates small H1-H3 trees and compares both PVS and alpha-beta against that minimax score. It checks null-window boundaries, strict parent wins versus ties and warmed-cache completion. The production leaf evaluator is shared explicitly: this establishes search arithmetic, not independent chess evaluation or playing strength. [Source-backed next-child plan](hard-stockfish-lessons.md).
- Candidate-only `CorroboratedVetoTest` rejects incomplete/tied vetoes, verifies real zero/one/ten-node interruptions and charges both completed proof frontiers. These tests accompany the external candidate until integration.
- `StudyFenContractTest` preserves standard-FEN double-push targets even when en passant is unavailable or pinned. The independent league runner initially compared a legal-EP-normalized chess.js FEN with standard FEN. The reproduced mismatch and failed attempt are retained; history-derived standard FEN now validates all six fields rather than ignoring en-passant differences.
- Existing source, history, clock, raw teacher, report and false-golden-answer guards remain in force. Failed child components and stopped catalog attempts stay visibly failed/incomplete in Extent.

Evidence root: `C:\work\sah`; collection `.studii/selective-repair/`. Its README indexes frozen recipes, original ATF component reports, raw teacher phases, Native ledgers, preflight failures, paired campaign and outcome bindings. Concurrent elapsed times are diagnostic; no general serial speed improvement, Android qualification, APK or release is claimed here.

The playing snapshots originate at `45b1196`. Live sync through `3707753` added release-module controls and puzzle chrome changes, with no changes to the playing classes or policies used by the frozen comparison. Recheck that boundary before any later integration; do not silently relabel a different Native baseline.
