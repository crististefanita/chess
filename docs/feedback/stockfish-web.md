# Stockfish coaching: Lite requirements and delivery gaps

## Required ownership

The requested product provider is **Stockfish Lite**, on Web and Android, including local/offline calculation. Full is not the requested package. The Windows executable is useful for offline investigation but cannot run directly inside a browser or Android APK. Basic, Advanced and Hard remain Native opponents; coaching must never silently execute Stockfish's move as Hard's decision.

| Surface | Required calculation | Component acceptance |
|---|---|---|
| AI game, every difficulty and either color | Stockfish Lite hint, score and played/recommended lines for the selected full history | Changing opponent level cannot select Native coaching or change the request policy. |
| Manual analysis board, local or hosted Web | Stockfish Lite at the selected position and explored branch | Back/forward, replay and branch changes reject old responses and preserve the source history. |
| Openings | Stockfish Lite for calculated recommendations, score and replay lines | Missing a stored reference cannot trigger Native search. Authored theory stays labelled as theory. |
| Evaluation bar and review graph | The same validated Stockfish observation as the displayed position | Correct color/perspective and ply; pending or failed work never displays a Native or stale score. WDL expected score is not labelled as a human win probability. |
| Interim and final Game Review | Stockfish Lite free-root and actual-played-root comparisons at a common completed depth | Complete ordered moves, source-bound scores, legal lines, accurate archive/restart behavior and no invented Native proof. |
| Puzzle explanations | Catalog solutions and deterministic rule verification retain their own provenance; any calculated coaching line or evaluation uses Stockfish Lite | Audit wrong-move/counter-reply and payoff displays too; a Native search shown as an explanatory hint is still a coaching route. |

Terminal rule results and captured-piece counters are game facts, not a substitute analysis provider. Stockfish computation is local; a Web cold start may still need the initial asset download. A new offline-start guarantee needs its own cache/install test.

## Remaining acceptance

| Priority | Gap | Completion evidence |
|---|---|---|
| P0 | Synchronize the Lite prototype with live code | Start from the current commit, preserve parallel opening-variety and three-close-line changes, and resolve API26 incompatibilities without changing their semantics. Never overwrite live with the older working-copy baseline. |
| P0 | Lite package and identity on both platforms | Independently pinned source, compatible small network, loader/executable, flavor and build options. Reject extra Full assets, a substituted manifest/network and a Full result entering a new Lite journal. Preserve older archived provider labels. |
| Closed · build | Android Lite native compilation | The disclosed unique-pointer CLI patch compiled successfully for ARM64, x86_64 and ARMv7, with API 26 and 16 KB ELF alignment. The package guard pins the actual executable bytes; native rendering remains a separate acceptance check. |
| P0 | Complete coaching-route audit | Cover every row above on hosted Web, localhost and Android. Review puzzle feedback and replay estimates, not only the main lightbulb. No silent fallback, board-only context or stale bar. |
| P0 | Offline study recorder | Existing Native hint-audit tools retain their legacy identity. New games need the actual Stockfish Lite observation at each White/Black pre-move frame plus the actual Native decision and complete final review. Preserve old receipts rather than relabelling Native hints as Stockfish or rebuilding missing observations retrospectively. |
| P0 | Trustworthy components and receipts | Legal complete PVs; score perspective; exact/completed MultiPV cohorts; matched review horizons; cancellation during startup/search/between plies; closed owners; cache/history identity; raw UCI; legacy archive compatibility; missing/foreign/modified receipt and PNG negatives. |
| P1 | Lite quality calibration | Compare actual choices and legal lines with a separately identified stronger teacher on frozen fresh contexts and justified sacrifices/recaptures. Record losses, disagreements and uncertainty. Smaller size or longer PVs do not establish equal strength to Full or Native Hard improvement. |
| P1 | Android persistence | Distinguish a completed durable review from a RAM prefix cache and a bounded diagnostic journal. Do not claim partial-review recovery after process death until it is implemented and tested. |
| Delivery owner | APK, RAG and publication | Another AI performs the signed build, minimal real-platform sanity, Extent qualification, README/release alignment and complete-download verification. This requirements task does not build or publish another APK. A storage-blocked install is `NOT_RUN`, never PASS. |

## Prototype handoff and evidence limits

Evidence root: `C:\work\sah`. Source work is retained in `.studii/stockfish-android-main`, based on `f47c9b2`; live had already advanced to `64af005` when this handoff was prepared. The next owner must sync again before integration.

- `.studii/stockfish-lite-web/HANDOFF.md` and `source-handoff.json`: Lite source/assets and independent release hashes are prepared; component, TeaVM and browser qualification for this Lite snapshot are **not executed**.
- `.studii/stockfish-android-build/lite-source-handoff.json`: the small compatible network and source are pinned. The earlier failure remains historical evidence. The integration build now compiles all three requested ABIs with the disclosed patch and retains their actual hashes in the native manifest; APK runtime acceptance is tracked independently.
- `.studii/stockfish-lite-quality/README.md` and `HARD-AB-NEXT.md`: the offline quality/Native A-B plans are drafts, not executed games or Elo results.
- The earlier Full prototype's component/host/build observations cannot be inherited by Lite. Its attempted emulator installation lacked storage. No existing game data was removed, no storage threshold was relaxed and no Android runtime PASS was obtained.

The integration candidate combines this handoff with the current live opening and complete-MultiPV changes. This document changes the required destination and records incomplete work; it does not claim that the source prototype or a new APK has been promoted.

## Current main baseline

The candidate routes calculated Web and Android coaching through their pinned Stockfish Lite 19.0.1 transports. Java remains the authority for legal moves, catalogs, history and storage. The implementation and qualification contracts live in [the Stockfish guide](../../web/stockfish.md).

Android platform acceptance and durable partial-work recovery remain separate gaps. The requested Lite provider covers all calculated coaching, including the bar and final review. The Native diagnostic register is not a backlog of newly measured Stockfish defects. [Native improvement protocol](../testing/hard-ab-elo.md)

- One lazy engine is shared across the page. Cancellation drains the old search, isolates consumers and rejects late results.
- Coaching validates full history, FEN, legal PVs, source identity, score perspective and comparable completed depths. Failure never silently selects Native Hard.
- Incremental Stockfish journals retain complete turns, resume after interruption and reuse only identical position histories. Final aggregation rejects missing, modified or foreign evidence.
- Native review versions remain readable and retain their provider. Explicit reanalysis replaces only the analysis, preserving the game ID and played history.
- Packaging verifies the independent upstream pins, full binary, GPL notices and corresponding source. Immutable runtime assets retain their bytes through Git publication.

## Android integration in progress

The target is Stockfish for every calculated hint, evaluation and analyzed line on the match board, manual board, openings and Game Review on both platforms. Native Basic, Advanced and Hard remain the opponents. Authored puzzle solutions and opening theory retain their catalog provenance; they are not renamed as Stockfish calculations.

At baseline `f47c9b2`, Android match/manual hints and review still use Native analysis. Openings reuse measured Stockfish references when the exact prefix is present and otherwise fall back to Native. The Android transport, packaging and UI migration are now in progress. These existing paths describe the migration gap, not the target: a missing reference or failed Stockfish calculation must produce an explicit pending, unavailable or retry state, with no Native analysis fallback.

Acceptance needs the following bounded evidence before declaring the APK qualified:

- Pin the Android engine source, executable build, supported ABI and required evaluation data; retain their digests, license and corresponding-source identity. WebAssembly pins do not certify a different Android binary.
- Exercise the actual packaged engine and retain `position`, full history, options, `go`, completed `info` rows and `bestmove` in raw UCI evidence. Record the actual process outcome separately from the orchestrator outcome.
- Validate the full FEN, legal complete PV, source/options identity and side-to-move score. Compare free and played roots at one common completed depth; reject bounds, incomplete MultiPV batches and foreign histories. Preserve actual nodes, selective depth and time separately from the requested cap.
- Cover all four Android surfaces, cancellation and A→B→A stale responses, activity recreation, close/restart and same-provider cache reuse. Reuse protocol/JVM negatives, then capture a small real APK journey in RO/EN; mocks and Web captures alone are insufficient.
- Keep archived Native reviews readable with their original label. Only explicit Stockfish reanalysis may replace analysis evidence; preserve game ID, played history and atomic rollback on failure.

A PV's number of moves is not its completed search depth or its strength. A longer legal line is not an exhaustive tactical proof. WDL diagnostics use `W + D/2` for the same side and context, retaining raw CP/mate scores when WDL saturates. Position diagnostics do not measure a candidate ΔElo; that needs separate source-bound paired games and uncertainty.

## Evidence and promotion

The manual analysis view must keep the board, position, score, hint and navigation together. The board stage must not absorb spare viewport height and push controls to the bottom. Reserve space for the hint so revealing it does not move the board; verify compact, tall and short landscape windows with enlarged text. This requirement is checked by component layout contracts and measured rectangles in the rendered sanity.

Protocol and journal fixtures publish their original Node assertions through ATF reporting. The compiled integration uses the actual shipped WASM and Java projection. The minimal rendered sanity publishes actions, assertions and embedded captures to Extent. Native numeric compatibility fixtures remain a separate reference scope and do not qualify Stockfish output.

Builds and reports belong under `C:\work\sah`, outside the checkout. [Testing strategy](../testing/README.md) and [Web publication](../../web/parity.md#publishing-the-link) govern the final source sync, qualification, static-tree promotion and verification of served bytes. Passing mocked tests alone cannot admit publication.

## Boundaries

Existing evidence qualifies the earlier Web baseline only. This requirements update does not qualify Lite on either platform. Native Hard remains open on its own measured evidence; it is not improved or recalibrated by assigning it Stockfish's strength. No APK or release is created in this documentation task.

The retired Native historical-search comparison in TeaVM exceeded its bounded process time during this integration. Its numeric expectations remain mandatory in JVM components. The original compiled-search benchmark remains available through explicit opt-in and is not reported as PASS when skipped; it is outside Stockfish Web promotion. Investigate its cost separately if Native coaching is ever reinstated on Web.

Publication recovery must preserve the qualified static identity. The combined upload/deploy job must not be rerun after a successful upload: that can create duplicate `github-pages` artifacts and correctly block deployment. The [publication procedure](../../web/parity.md#publishing-the-link) now uses a fresh manual dispatch with the same immutable commit and digest, followed by independent served-byte and browser verification.

## Concurrent opponent and coaching

Stockfish supplies the displayed hint and score; Native Basic/Advanced/Hard supplies the opponent move on an independent worker. Components stall Stockfish and compare the actual Native move, completed horizon and work before and during the stalled request. The compiled Web probe performs the Native calculation before releasing a held coaching request. Cancellation and failure must not transfer either provider’s result into the other role.
