# Native Hard: checked-frontier study

[Current priorities](../feedback/hard.md) · [A/B and Elo protocol](hard-ab-elo.md) · [Improvement process](hard-progress.md)

## Scope

The public opponent stays `LEGACY`. The experimental baseline is `c396c32`; test/documentation integration uses current main separately. Stockfish Lite owns product hints, the bar and review. No APK is built here.

The child closes a checked leaf through all legal evasions, recursively while check continues. Visits are charged to the same adaptive 100k–200k extra-node allowance; budget/safety exhaustion interrupts the iteration. This is check-only closure, not complete capture quiescence. Components cover cold/warm/disabled cache, narrow/full windows, interruption and a sound queen sacrifice.

The [Stockfish implementation](https://github.com/official-stockfish/Stockfish/blob/master/src/search.cpp) separates check handling from static evaluation. This supports the local correction, not a strength claim. [Stockfish development guidance](https://official-stockfish.github.io/docs/stockfish-wiki/Developers.html) also requires testing functional search changes.

## Results and rejection

| Check | Actual result | Meaning |
|---|---|---|
| training-012 stored checked leaf | Static +8 cp; free-evasion minimax +901 cp for the recapturing side | Reproduced local defect. These are Native internal scores, not Stockfish's scale. |
| Focused prototype | 58/58 checks | Component correctness does not qualify stronger play. |
| Full first-choice catalog | Parent 1,214/1,238; diagnostic child 1,208/1,238 | Seven recovered keys, twelve lost root keys and one rejected receipt. Whole puzzle lines are not certified. |
| Basic/Advanced | 48/48 exact pairs | Choices, ordered PVs, depth and extra-node work remain identical. |
| training-021 | Qxf5+/Qxf4+ loses 500/498 per-mille WDL at Stockfish H17/H19 | Both color-equivalent controls reject promotion. |
| Three tactics | tactic-020/031/052 loses 500/1,000/901 per-mille at H19 | Confirmed losses, not different catalog spellings. |
| Paid H3 normalization | 35/35 checks after an API26 correction; still loses training-021 at completed H6, under 40k extra nodes | Mixing the old H3 seed with new frontier semantics was insufficient to explain the failure. No full catalog or match was run for this rejected child. |

The strict catalog stops at `training-097`: a legacy capture-verification continuation cannot be labelled solely as check evasion. A separate diagnostic execution retained all 1,238 observations with that receipt explicitly rejected and excluded from accepted results. The working inference `training-055` was wrong because catalog order is not numerical.

Existing governance also caught a newer Java List accessor in the first normalized prototype. The corrected source uses the API26-compatible indexed accessor. Failed and corrected runs are retained separately.

## Correct root and incorrect explanation

In training-021 the parent chooses the winning Qg7+, but its stored PV is `Qg7+ Ke8 Qg8+ Kd7 Qxd8+`. Black can legally play Kxd8. The color-reversed line has the same defect. Stockfish confirms the first move's value, not this Native continuation.

`NativeFrontierRegressionTest` separates five root-only winning controls from two stored-PV counterexamples. Captured teacher evidence retains 42 exact H17/H19 phases across seven contexts. The two training-065 orientations remain study goals: the child recovers only one orientation. This partial recovery is not a passing Native promotion claim.

The test-only YAML fixtures retain explicit origins/history, original UCI, binary identity and an integrity pin. Offline validation replays legal PVs, checks raw scores/WDL/depth and rejects changed board, horizon or best move. The stored bad-PV lesson never requires a future engine to retain that bad line. Neither fixture is a runtime move override.

## Next bounded intervention

The follow-up [current calibration](current-profile-calibration.md) retains a quiet-plan component: after `Qg7+ Ke6`, Qd6 pins Rg3 to Kh2. The captured Stockfish H19 line uses **Kh3**, then meets Qb8 with **Re3+**. The synthetic bishop-on-f5 counterexample makes Kh3 illegal. This is a general legality/plan lesson, not an unconditional king-move rule or a runtime FEN override.

Isolated selected-root probes of the checked-frontier child still prefer Qxf5+ over Qg7+: at H6, +90 versus −20 Native cp; at H8, +89 versus −15. Both H10 probes exhaust their own two-million-node allowances without an exact score. These are independent per-root probes of the rejected child, not actual shared public-game budgets. More depth through H8 did not fix the ranking; incomplete H10 cannot be reported as a success. Inspect free quiet defenses and the quiet unpin plan before another budget-only retry.

Compare Qg7+ with Qxf5+ using free defenses at the same ordinary and forcing horizon, retaining Kxd8 in the captured-rook line. Inspect why Native prefers Qf6 over Ke7 after Qxf5+: the isolated H6 windows score them +90/+92. The Ke7 branch remains +91 at H8; the other H8 branch does not finish within its own 200k probe. These isolated probes do not form a shared full-game allowance or prove a draw.

The rejected actual training-021 decisions finish H6 with budget remaining, so node exhaustion alone cannot explain the failure. Preserve root and full-continuation quality as separate gates. Any future receipt must name its actual forcing mode and charged work; never widen a check-only certificate to accept arbitrary quiet extra plies. Excluded-family transfer and paired games follow the relevant gates.

## Evidence

Constant root: `C:\work\sah`; relative study `.studii/native-frontier/`. Its index links original Extent reports, manifests, Native ledgers and teacher phases. Teacher join IDs are posthoc, not original Native run IDs. Consumed contexts and mirrors are not new independent families.

No completed match was played: **Delta Elo = NOT_MEASURED**. Failed children remain external; qualified regression fixtures and documentation can be integrated independently.
