# Stockfish Lite coaching: acceptance and remaining work

## Provider contract

Stockfish Lite owns calculated hints, the evaluation bar and interim/final Game Review on Web and Android. Native Basic, Advanced and Hard remain independent opponents. Stored opening references and authored puzzle solutions retain their original source. [Implementation and verification](../../web/stockfish.md) · [Native improvement protocol](../testing/hard-ab-elo.md)

| Surface | Required evidence |
|---|---|
| AI game, either color and every opponent level | One exact full-history Lite observation supplies the bar and up to three close recommendations. Stalled or failed coaching cannot block or alter the Native opponent. |
| Manual board and Openings | Branch/cursor changes reject stale results; source-sized replay preserves the board and selected line. Missing stored reference never selects Native coaching. |
| Interim and final review | Free and played roots share a completed depth and provider. Archive reopening preserves identity; partial or foreign work cannot be labelled complete. |
| Puzzle explanations | Catalog replay remains verified rule knowledge. Any calculated coaching line or score needs its declared Lite provider; audit wrong-move and payoff routes separately. |

The vertical bar now uses the shared gradual centipawn projection, independently of WDL saturation. Raw Stockfish WDL remains review evidence; neither value is human Elo. The scale and both production adapters have offline component coverage. A longer PV is not a deeper completed search or stronger play. [Source conventions](../board/analysis-sources.md)

## Verified edition

- Live opening-variety and completed-MultiPV changes are retained in the integration candidate. Both adapters cap visible recommendations at three; an interrupted deeper batch retains the latest full exact cohort.
- Web ships the independently pinned Lite loader/WASM and a distinct Lite journal namespace. New Lite journals cannot inherit old Full observations. Older archives retain their source.
- Android Lite compiles for ARM64, x86_64 and ARMv7 at API 26 with 16 KB LOAD alignment. The disclosed native-only unique-pointer CLI patch and reproducible recipe accompany the source archive; binary/data/source pins are checked during APK builds.
- Components and compiled Web integration verify legal PVs, exact history, score perspective, complete depths, cancellation, archives and independent opponent progress. Read the current Extent counts rather than inheriting an earlier prototype's PASS.

- The MultiPV correction rejects duplicate roots and retains the last completed depth with distinct alternatives. The six-ply saved line reproduces the failure and verifies aggregation without extra searches; a repeated move cannot become a second recommendation.

## Remaining work in priority order

| Priority | Gap | Closure evidence |
|---|---|---|
| P1 | Android durable partial review work | Persist and revalidate completed per-ply free/played observations after process death. Current Android RAM reuse and durable completed reviews are separate scopes. Verify missing work, branches, failed writes, history/provider changes and final aggregation. |
| P1 | Offline exact-bestmove completion | The predeclared recorder retains complete Lite coaching in 4/6 games. Two Advanced games expose three unavailable hints and two unavailable reviews: `No exact PV for Stockfish bestmove`. Retain the original failed UCI transcript with context/provider/work in the failure receipt, reproduce with a component transport, and complete the exact-bestmove/common-depth contract without substituting another root. Preserve fail-closed behavior and verify fresh games; failed advice never alters the opponent or earns learning admission. [Measured scope](../testing/current-profile-calibration.md#collector-failures-and-technical-continuation) |
| P1 | Lite quality calibration | Compare fresh independent contexts and sacrifices/recaptures with a separately identified stronger teacher; record CP/mate/WDL disagreements and uncertainty. Package size does not establish Full-equivalent strength or Native Elo. |
| P2 | Lifecycle and rendering matrix | Complete RO/EN enlarged-text rotation, open replay restoration and cancellation/restart observations on the signed APK and served Web artifact. Components establish their own scope. |

## Evidence and promotion

[Edition 0.17.1](../../release/0.17.1/reports/README.md) closes the identified acceptance: 735 components, 67 native assertions on the exact APK, Web review and three tabs observed through MCP, and complete downloads from the main README on both channels. The manual scope is explicit; the broader rendering matrix and P1 work above remain open.

Keep build, raw UCI, original receipts and self-contained Extent under `C:\work\sah`. The current integration study is `studii/2026-11-26-stockfish-lite-release`; older `.studii/stockfish-lite-web` and `.studii/stockfish-android-build` handoffs remain historical input, including failed Full installs and pre-patch native compilation. They are not Lite qualification.

The release process requires a clean candidate, independently checked package, component/governance evidence, bounded native sanity, paired EN/RO documentation and complete README-to-APK download verification. Pages publication preserves immutable runtime bytes and verifies the served bundle after deployment. [Release procedure](../release/README.md#qualification-and-publication) · [Web publication](../../web/parity.md#publishing-the-link)

Close only the acceptance demonstrated by the identified run. Retain unresolved lifecycle/persistence/quality work here; move durable behavior into the implementation guide. Native Hard's own strength feedback remains open on its separate measurements.
