# Experimental Hard A engine

## Roles and integration

`HardCandidateSearch` is an explicit experiment, separate from the existing public algorithm. `HardMaster` adds match history, the repertoire, policy and generic mate proof. The [selector](../../chess-core/src/main/resources/hard-runtime.yaml) remains `LEGACY`: neither the presence of A nor a passing mechanism report promotes it into play.

| File | Responsibility |
|---|---|
| [Master policy](../../chess-core/src/main/resources/hard-master.yaml) | Experimental reference, separate core and certificate allowances |
| [Next-candidate policy](../../chess-core/src/main/resources/hard-next.yaml) | Versioned settings and mechanisms, only for explicit comparisons |
| [Search](../../chess-core/src/main/java/ro/atf/chess/HardCandidateSearch.java) | Iterative alpha-beta, optional PVS, quiescence and result bounds |
| [Adapter](../../chess-core/src/main/java/ro/atf/chess/HardMaster.java) | Context, executable decision and complete calculation accounting |

The integration preserves `start(setup, openingChoice)` and `openingChoice()`, the `5|` checkpoint, history branching and the public repertoire. `TacticalAnalysis` retains the implementation from main; only the existing capture-order helper becomes accessible to the new core. The A preset does not replace native evaluation or bonuses.

## Result contract

Scores use the side-to-move perspective. `EXACT`, `LOWER` and `UPPER` have different meanings: a bound is not an exact final score. Interruption keeps only the last completed iteration. If none exists, analysis has no score and the play adapter refuses to invent a move.

The A certificate is isolated in `HardMateCertificate`; `ForcedMateSearch` and its native API remain identical to origin/main. The AND/OR mate proof explores every defense and does not read puzzle solutions, memorized FENs or catalog IDs. Only `PROVED` replaces the core; an inconclusive attempt retains its reported cost and does not consume the separately declared core allowance.

The numeric TT retains reversible history, the counter, distance and horizon. A move suggestion without a score can be reused only after checking legality again. Changing evaluators invalidates scores from another scope. There is no implicit learning memory or unconditional transfer between games.

The application's automatic fivefold/75-move draws remain distinct from the experimental threefold/50-move claim policy. Extracting only a FEN can lose that information.

## Named tactical frontier

Schema 5 explicitly declares `quiet-tactics`. The historical schemas 1–4 and previous constructors keep `NONE`; they do not acquire a new frontier silently.

| YAML value | Policy | Bounded search behavior |
|---|---|---|
| `none` | `NONE` | Existing ordinary capture/check-evasion frontier |
| `forcing-pins` | `FORCING_PINS` | Mode 7: two quiet checks, one quiet queen-pin threat/reinforcement and the corresponding legal defensive replies |

`forcing-pins` requires a new A identity, v6 or later. The core/certificate allowances are unchanged; all extra frontier visits remain charged. The normal `HardMaster` adapter reads this named policy. A study may override the numeric mode explicitly, but that is diagnostic evidence, not qualification of the configured candidate.

`decideObserved` returns the action and its one actual search report. Only an unsearched `BOOK` action may omit the report. A completed action must match the exact PV root, horizon, total cost, stop reason and certificate. Incomplete evidence can be observed without a move, while the ordinary play adapter rejects it. This prevents a league from rerunning a different search merely to obtain a PV.

## Measured repair of training-078

The named `hard-a-v6` candidate passes all **11/11 material guards**, preserving the ten previous recoveries. The actual configured-policy gate has no numeric frontier override: it reads schema 5 with `FORCING_PINS` and calls `decideObserved`, so the action, PV, horizon and work belong to the same calculation. The public selector remains `LEGACY`.

The cause was a missing quiet forcing continuation: after two quiet rook checks and the exchanges, a rook can add a legal attack to an already absolutely pinned queen. Mode 7 includes this bounded battery reinforcement and the defender's legal replies. Off-axis attacks by an absolutely pinned attacking piece are rejected. Components include counterexamples, narrow-window/cache/incomplete-proof cases, both colors, and transfer to different files and diagonals checked against legal move generation independently of the pattern heuristic.

In `training-078`, the candidate chooses `Rxe6` (`e4e6`) with a completed H5 value of +286 in its own score model. The receipt reports `EXACT` for that completed horizon and `NODE_LIMIT` for the later interrupted iteration; this does not claim a completed H8 or a perfect chess evaluation. Its 1,003,134 total visits comprise 1,000,000 core visits and 3,134 inconclusive mate-certificate visits. The captured UCI PV is:

```text
e4e6 c6f3 e6e7 g7h8 e7e8 c8e8 e1e8 h8g7 g4d7 f3f7 e8e7 b7d5 e7f7 d5f7
```

Stockfish 19 independently checked all **seven White decisions on this supplied PV** at H17/H19: each retained 1,000‰ expected WDL, with zero loss against the free best move at both depths. This is scoped evidence for the recorded continuation, not a complete-game or universal-strength claim.

The actual configured-play [continuation probe](../../atf-tests/src/test/java/ro/atf/chess/Hard078ContinuationStudyTest.java) separately passes **2/2 contexts** after the four legal prefix plies. After `Rxe6 Qxf3 Re7+ Kf8`, A6 chooses `Rf1` at completed H7 with 1,004,711 total visits. After `Rxe6 Qxf3 Re7+ Kh8`, it chooses `Re8` at completed H7 with 1,003,819 total visits. Both receipts preserve the real action, PV and total cost, including the inconclusive mate proof; `NODE_LIMIT` describes the later interrupted iteration. Receipts remain in `.studii/hard-078-repair/publication-continuations/` under the study root. These two approved continuations are separate from the eleven-root gate and do not establish a complete-game result.
The policy targets H8/Q32, TT 22k, a 1M core allowance and a separate maximum 100k mate allowance, with a 1.1M total ceiling. The next strength comparison is **A6 / FORCING_PINS against A5 / NONE on 100 new color-reversed pairs at those identical allowances**. Its ΔElo and 95% CI are not inferred from the guard results. The candidate's complete catalog, independent transfer and conversion gates remain open before any product promotion.

Source-bound plans, decisions and Extent remain under `SAH_STUDY_ROOT`, in `.studii/hard-078-repair/publication-named-gate/` and the associated `reports/` directory. The first folder's `plan.txt` records `explicitOverride=false`; `verdict.txt` records `accepted=11/11` and `failedRoots=[]`. These are source-study results, not qualification of an APK.

## Reproducible confirmation

The [confirmation harness](../../atf-tests/src/test/java/ro/atf/chess/HardConfirmationLeague.java) uses seed `93714689`, ten prefixes in each of ten opening families, prior equivalent-start exclusions, and 100 color-reversed pairs. Its absolute cap is 220 plies. The plan fingerprints compiled classes/resources, actual runner/statistics and copied exclusions.

The opt-in [study phases](../../atf-tests/src/test/java/ro/atf/chess/HardConfirmationStudyTest.java) are:

1. `freeze` fixes the corpus, identities and copied exclusions before any engine game. Subsequent phases reject changed inputs.
2. `run` records each actual observed action once, including its completed `EXACT` score, PV and total core/certificate work. Book actions are declared separately; a missing playable result stays explicitly unavailable.
3. `replay` uses the frozen compiled snapshot to reconstruct legal histories, PVs, costs and every color pair before deriving W/D/L/unfinished, ΔElo, a 95% family-cluster interval and unfinished-game sensitivity bounds.

The [seven coverage components](../../atf-tests/src/test/java/ro/atf/chess/HardConfirmationCoverageTest.java) reject missing/foreign/duplicate pairs, truncated or altered ledgers, changed budgets, substituted corpus/exclusions and changed actual harness bytecode. They also verify deterministic freshness and SAN.

The bootstrap interval is approximate with only ten family clusters. Publish numerical strength results only after both `run` and `replay` pass with zero unavailable decisions, and retain the unfinished-game sensitivity bounds. This experiment does not claim a complete hint/Game Review audit or promotion. The paired run is pending, so no new ΔElo is claimed.

## Android compatibility of the new core

The new A classes keep the API26 collection contract: ordinary indexing instead of `getFirst/getLast`, `Collectors.toList()` instead of the newer stream terminal, and `HardCollections` for defensive factories. Lists and sets remain immutable; null elements and repeated set members are rejected. This integration does not rewrite the remote classes.

The Android references for [List](https://developer.android.com/reference/java/util/List) and [Set](https://developer.android.com/reference/java/util/Set) identify modern factories as API30+, while sequenced collections are newer still. The Java language version does not guarantee that those methods exist on a device.

`HardAndroidApiTest` scans the nine A classes' ASTs, excluding examples in comments/strings, and verifies the helper's semantics. This focused guard does not replace whole-application lint or Android sanity.

## Verification and strength

Core components compare with independent minimax, with and without cache, narrow/full windows and interruption. Other components preserve main's APIs, metadata and Basic/Advanced/Hard routes while the selector is LEGACY. All have ATF/Extent reporting and belong to component or governance suites.

Example from an already compiled external checkout, with JDK21 and the temporary directory/Maven repository configured under the study root:

```powershell
mvn -pl atf-tests test '-Dtest=HardCandidateSearchTest,HardCandidatePvsTest,HardMasterRemoteCompatibilityTest,SuiteGovernanceTest#everyJvmTestHasExactlyOnePrimarySuiteAndAnExtentAudit' "-Dchess.extent=$studyRoot/reports/master-components-new.html"
```

Use a fresh Extent path for each execution. Data and reports remain under `SAH_STUDY_ROOT`, with source and receipt inventories relative to it.

Results from an older checkout do not qualify this integration: the evaluator and public infrastructure may differ. Strength must be measured on the actual published source, with the same starts, reversed colors, explicit allowances and confidence intervals. Correctness components are not an Elo measurement.

[Feedback and promotion gates](../feedback/hard.md)