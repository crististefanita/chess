# Stockfish Web integration

## Implemented scope

Web hints, the manual analysis board, opening-study analysis and new Game Reviews use the pinned full single-thread Stockfish 19.0.1 engine. Java remains the authority for legal moves, catalogs, history and storage. The implementation and qualification contracts live in [the Stockfish guide](../../web/stockfish.md).

- One lazy engine is shared across the page. Cancellation drains the old search, isolates consumers and rejects late results.
- Coaching validates full history, FEN, legal PVs, source identity, score perspective and comparable completed depths. Failure never silently selects Native Hard.
- Incremental Stockfish journals retain complete turns, resume after interruption and reuse only identical position histories. Final aggregation rejects missing, modified or foreign evidence.
- Native review versions remain readable and retain their provider. Explicit reanalysis replaces only the analysis, preserving the game ID and played history.
- Packaging verifies the independent upstream pins, full binary, GPL notices and corresponding source. Immutable runtime assets retain their bytes through Git publication.

## Evidence and promotion

The manual analysis view must keep the board, position, score, hint and navigation together. The board stage must not absorb spare viewport height and push controls to the bottom. Reserve space for the hint so revealing it does not move the board; verify compact, tall and short landscape windows with enlarged text. This requirement is checked by component layout contracts and measured rectangles in the rendered sanity.

Protocol and journal fixtures publish their original Node assertions through ATF reporting. The compiled integration uses the actual shipped WASM and Java projection. The minimal rendered sanity publishes actions, assertions and embedded captures to Extent. Native numeric compatibility fixtures remain a separate reference scope and do not qualify Stockfish output.

Builds and reports belong under `C:\work\sah`, outside the checkout. [Testing strategy](../testing/README.md) and [Web publication](../../web/parity.md#publishing-the-link) govern the final source sync, qualification, static-tree promotion and verification of served bytes. Passing mocked tests alone cannot admit publication.

## Boundaries

This change qualifies Web coaching. Native AI opponents and Android retain their own providers and qualification status. The Native Hard feedback remains open on its own measured evidence; it is not closed by assigning Stockfish's strength to Hard. No Android release is created here.

The retired Native historical-search comparison in TeaVM exceeded its bounded process time during this integration. Its numeric expectations remain mandatory in JVM components. The original compiled-search benchmark remains available through explicit opt-in and is not reported as PASS when skipped; it is outside Stockfish Web promotion. Investigate its cost separately if Native coaching is ever reinstated on Web.

Publication recovery must preserve the qualified static identity. The combined upload/deploy job must not be rerun after a successful upload: that can create duplicate `github-pages` artifacts and correctly block deployment. The [publication procedure](../../web/parity.md#publishing-the-link) now uses a fresh manual dispatch with the same immutable commit and digest, followed by independent served-byte and browser verification.
