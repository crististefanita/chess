# Stockfish Lite coaching: acceptance and remaining work

## Provider contract

Stockfish Lite owns calculated hints, the evaluation bar and interim/final Game Review on Web and Android. Native Basic, Advanced and Hard remain independent opponents. Stored opening references and authored puzzle solutions retain their original source. [Implementation and verification](../../web/stockfish.md) · [Native improvement protocol](../testing/hard-ab-elo.md)

| Surface | Required evidence |
|---|---|
| AI game, either color and every opponent level | One exact full-history Lite observation supplies the bar and up to three close recommendations. Stalled or failed coaching cannot block or alter the Native opponent. |
| Manual board and Openings | Branch/cursor changes reject stale results; source-sized replay preserves the board and selected line. Missing stored reference never selects Native coaching. |
| Interim and final review | Free and played roots share a completed depth and provider. Archive reopening preserves identity; partial or foreign work cannot be labelled complete. |
| Puzzle explanations | Catalog replay remains verified rule knowledge. Any calculated coaching line or score needs its declared Lite provider; audit wrong-move and payoff routes separately. |

The bar's WDL expected score is not a human win probability or Elo. A longer PV is not a deeper completed search or stronger play. [Source conventions](../board/analysis-sources.md)

## Implemented candidate

- Live opening-variety and completed-MultiPV changes are retained in the integration candidate. Both adapters cap visible recommendations at three; an interrupted deeper batch retains the latest full exact cohort.
- Web ships the independently pinned Lite loader/WASM and a distinct Lite journal namespace. New Lite journals cannot inherit old Full observations. Older archives retain their source.
- Android Lite compiles for ARM64, x86_64 and ARMv7 at API 26 with 16 KB LOAD alignment. The disclosed native-only unique-pointer CLI patch and reproducible recipe accompany the source archive; binary/data/source pins are checked during APK builds.
- Components and compiled Web integration verify legal PVs, exact history, score perspective, complete depths, cancellation, archives and independent opponent progress. Read the current Extent counts rather than inheriting an earlier prototype's PASS.

## Remaining work in priority order

| Priority | Gap | Closure evidence |
|---|---|---|
| P0 | Current platform and publication acceptance | Identified signed APK on the emulator and served Lite Web bundle through Browser MCP, with original assertions, UCI, source/build identity and captures. A build or a Full predecessor's report is insufficient. |
| P1 | Android durable partial review work | Persist and revalidate completed per-ply free/played observations after process death. Current Android RAM reuse and durable completed reviews are separate scopes. Verify missing work, branches, failed writes, history/provider changes and final aggregation. |
| P1 | Offline study recorder | Retain actual Lite observations before each White/Black move, the actual Native decision and the complete final review. Do not retrospectively rename reconstructed hints as observed. |
| P1 | Lite quality calibration | Compare fresh independent contexts and sacrifices/recaptures with a separately identified stronger teacher; record CP/mate/WDL disagreements and uncertainty. Package size does not establish Full-equivalent strength or Native Elo. |
| P2 | Lifecycle and rendering matrix | Complete RO/EN enlarged-text rotation, open replay restoration and cancellation/restart observations on the signed APK and served Web artifact. Components establish their own scope. |

## Evidence and promotion

Keep build, raw UCI, original receipts and self-contained Extent under `C:\work\sah`. The current integration study is `studii/2026-11-26-stockfish-lite-release`; older `.studii/stockfish-lite-web` and `.studii/stockfish-android-build` handoffs remain historical input, including failed Full installs and pre-patch native compilation. They are not Lite qualification.

The release process requires a clean candidate, independently checked package, component/governance evidence, bounded native sanity, paired EN/RO documentation and complete README-to-APK download verification. Pages publication preserves immutable runtime bytes and verifies the served bundle after deployment. [Release procedure](../release/README.md#qualification-and-publication) · [Web publication](../../web/parity.md#publishing-the-link)

Close only the acceptance demonstrated by the identified run. Retain unresolved lifecycle/persistence/quality work here; move durable behavior into the implementation guide. Native Hard's own strength feedback remains open on its separate measurements.