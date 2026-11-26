# Hard · open priorities

The public selector is `LEGACY`. A3 remains experimental: the initial 15.5% CPU saving failed confirmation on dense contexts, where CPU increased by 8.85%. It is not promoted as a general optimization and has no demonstrated Elo gain. [Results and limits](../testing/hard-current-variants.md) · [Ordered steps](../testing/hard-progress.md) · [A/B and Elo protocol](../testing/hard-ab-elo.md)

## Current priorities

The [six-copy continuation study](../testing/hard-continuation-copies.md) finds a local H8 signal and a selective candidate with +8 first keys / +2 complete routes, but no candidate qualifies. Six initial oracle clock mismatches are corrected and retained. Reference agreement is no longer used as a substitute for independent WDL quality.

| Priority | Open step | Closure condition |
|---|---|---|
| P1 | HARD-03 · selective continuation regressions | Preserve the selective child's gains, but repair training-056/091/096 before promotion: they lose WDL against free H17/H19 defenses. The new actual Native route components pass on main and fail on that child. Compare the first wrong evaluation and the open frontier at a common horizon; no FEN fallback or budget-only retry. Keep the original rook, queen and sound-sacrifice guards. The old checked-frontier/PV closure goal remains open. [Six-copy results](../testing/hard-continuation-copies.md) · [Frontier mechanism](../testing/native-forcing-frontier.md) |
| P1 | HARD-08 · poisoned curriculum reference | training-060's Kxg2 payoff contradicts the original Lichess solution and loses at both teacher horizons; e4 wins. Correct the reference and goal with source-attested, objective-compatible continuation, preserved ID/progress, hint and payoff tests. Do not weaken the closed +200 material threshold or turn a losing baseline into a golden answer. Audit other material references before learning from them. [Evidence and objective mismatch](../testing/hard-continuation-copies.md#genuine-regression-versus-curriculum-mismatch) |
| P1 | HARD-04 · complete learning admission | The predeclared Lite recorder now retains actual observations on both turns and complete reviews for 4/6 games. Three unavailable hint attempts and two unavailable Advanced reviews stay explicit and excluded from learning. Capture failed raw UCI and resolve the exact-bestmove completion gap with the provider owner, then verify fresh complete games. Never relabel old Native journals or all 300 rating PGNs as Lite-certified learning data. [Provider work](stockfish-web.md) |
| P1 | HARD-05 · transfer and Delta Elo | All five experimental gates fail; no candidate Delta Elo is measured. After the three genuine regressions and reference admission are resolved, freeze excluded families, convert games to result and measure a separate paired Delta Elo. The unchanged baseline remains Hard 1,663 relative to the configured anchor, with descriptive 95% envelope 1,531…1,763 and 13 unfinished games. [Baseline limits](../testing/current-profile-calibration.md) |
| P2 | HARD-06 · next speed optimization | Investigate sparse packing's loss on dense contexts. Profile and test one hypothesis, such as density-based selection or another measured cost. Keep A3 as an experimental control and require dense and sparse cases before promotion. |
| P2 | HARD-07 · transferable lessons | Extract YAML puzzle families from first confirmed WDL losses: general conditions, best defense, counterexample and excluded cases. Measure first choice, whole line and conversion separately. Memorizing a FEN is not playing progress. |

## Ownership and limits

Stockfish **Lite** owns the target for calculated hints, the evaluation bar, openings, manual board, calculated explanations and Game Review on Web and Android. [Integration gaps and required checks](stockfish-web.md) belong there. APK/RAG and publication belong to the other delivery owner. Basic/Advanced/Hard remain Native opponents.

Source freeze, actual baseline, YAML budget, cache, family partitions and audit rules belong to the [protocol](../testing/hard-ab-elo.md), rather than repeated ToDo items. Compare algorithm, budget and repertoire separately. Preserve required depths and replies; time is a measurement, not a reason to weaken play.

A1/A2 and failed aspiration remain study results rather than active priorities. Rejected forcing experiments are retained in the [frontier study](../testing/native-forcing-frontier.md); component correctness is not a playing promotion. Protected training-078 repairs and continuations belong to the [technical contract](../board/hard-candidates.md#measured-repair-of-training-078). Reopen them only for a new regression.

Neither the narrow window nor general H4/H6 corroboration is promoted. The complete 1,238-first-choice catalog falls 1,214 → 1,213 in the first probe. Stockfish H17/H19 confirms the training-012 loss. NativeHorizonGuardTest preserves the tactic in both colors and the unfinished-exchange/sound-sacrifice YAML pair. Full results are in the [technical record](../testing/hard-current-variants.md); repair the frontier cause before retrying the same strategy with new thresholds.

Optional metadata compatibility passed the original regression and new checks: [archive contract](../testing/hard-audit.md#optional-decision-metadata-and-old-receipts). This is closed work; it does not establish a Lite journal implementation.

External evidence root: `C:\work\sah`; current experiment `.studii/hard-continuations/`, baseline `.studii/native-measurement/`, frontier diagnosis `.studii/native-frontier/`. Keep the identified children external while their gates fail. Do not relabel exploratory position gains or a repaired curriculum score as measured Elo.
