# Hard · open priorities

The public selector is `LEGACY`. A3 remains experimental: the initial 15.5% CPU saving failed confirmation on dense contexts, where CPU increased by 8.85%. It is not promoted as a general optimization and has no demonstrated Elo gain. [Results and limits](../testing/hard-current-variants.md) · [Ordered steps](../testing/hard-progress.md) · [A/B and Elo protocol](../testing/hard-ab-elo.md)

## Current priorities

| Priority | Open step | Closure condition |
|---|---|---|
| P1 | HARD-03 · tactical frontier before another root adjustment | The cheaper probe loses `training-012`: `…Rb1` H4 becomes `…h5` H5, ending at `…Qxf2` before legal recaptures. H4/H6 corroboration repairs Larsen but loses 500/498‰ WDL in `training-021` in both colors. Repair exchange/reply evaluation in components, with a sound sacrifice as counterexample; preserve the veto and catalog. |
| P1 | HARD-04 · fully auditable games | Before each White/Black move, retain the actual Native decision, Stockfish Lite hint, complete history and final analysis. Archive compatibility and delayed responses are verified. Never relabel an old Native journal as Stockfish. |
| P1 | HARD-05 · transfer and Delta Elo | Excluded families and conversion to result. A legal candidate may receive a diagnostic pilot; promote only after gates and confirmation on 100 new pairs, with pair-aware 95% CI and unfinished games separate. |
| P2 | HARD-06 · next speed optimization | Investigate sparse packing's loss on dense contexts. Profile and test one hypothesis, such as density-based selection or another measured cost. Keep A3 as an experimental control and require dense and sparse cases before promotion. |
| P2 | HARD-07 · transferable lessons | Extract YAML puzzle families from first confirmed WDL losses: general conditions, best defense, counterexample and excluded cases. Measure first choice, whole line and conversion separately. Memorizing a FEN is not playing progress. |

## Ownership and limits

Stockfish **Lite** owns the target for calculated hints, the evaluation bar, openings, manual board, calculated explanations and Game Review on Web and Android. [Integration gaps and required checks](stockfish-web.md) belong there. APK/RAG and publication belong to the other delivery owner. Basic/Advanced/Hard remain Native opponents.

Source freeze, actual baseline, YAML budget, cache, family partitions and audit rules belong to the [protocol](../testing/hard-ab-elo.md), rather than repeated ToDo items. Compare algorithm, budget and repertoire separately. Preserve required depths and replies; time is a measurement, not a reason to weaken play.

A1/A2, the evasion frontier and failed aspiration remain study results rather than active priorities. Protected training-078 repairs and continuations belong to the [technical contract](../board/hard-candidates.md#measured-repair-of-training-078). Reopen them only for a new regression.

Neither the narrow window nor general H4/H6 corroboration is promoted. The complete 1,238-first-choice catalog falls 1,214 → 1,213 in the first probe. Stockfish H17/H19 confirms the training-012 loss. NativeHorizonGuardTest preserves the tactic in both colors and the unfinished-exchange/sound-sacrifice YAML pair. Full results are in the [technical record](../testing/hard-current-variants.md); repair the frontier cause before retrying the same strategy with new thresholds.

Optional metadata compatibility passed the original regression and new checks: [archive contract](../testing/hard-audit.md#optional-decision-metadata-and-old-receipts). This is closed work; it does not establish a Lite journal implementation.

External evidence: root C:\work\sah, relative folders .studii/native-hard-current/ and .studii/native-hard-next/. With no new completed games, Delta Elo = NOT_MEASURED. Do not repeatedly recalibrate unchanged live code or inherit the oracle's rating.
