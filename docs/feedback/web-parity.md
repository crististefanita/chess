# Web · Parity with the Chess Lab APK

## Overview

The register tracks the remaining differences between APK and Web.
[Engine and adapters](../../web/README.md#shared-engine-and-platform-adapters) ·
[APK–Web matrix](../../web/parity.md)

## What We Reuse from J4

Card Table Browser 0.2.0 and its test JAR provide card table contracts.
Extracting a game-independent utility into shared J4/Chess code makes sense when
the evidence identifies a consumer in each project, a stable API, and duplicate
code that can be removed. The candidates are revision-based transport and
browser evidence collection.
[Card Table Browser 0.2.0 packages](https://github.com/crististefanita/atf_j4/tree/3d2b7b11eedd6aadd48a82fd7858ef195e21f12e/atf-ui-browser/lib)

## WEB-01 · Engine and Application Contract

**P1 · Chess owner.** Gradually extract session transitions that require
equivalent decisions in `ChessActivity`, `AnalysisActivity`, and the Web
adapter. Start with resuming, canceling, and receiving a delayed AI reply.
[Engine and projection](../testing/README.md#engine-and-projection)

**Resolved 2026-11-22 · Web compilation · Hard proof digest.** `PortableSha256` emits the same standard SHA-256 bytes without JCA provider lookup. `PawnPushProof` and `GameReviewCodec` use it, preserving their existing lowercase-hex and Base64 identifiers. `PortableSha256Test` covers standard vectors, padding boundaries, JDK parity, and both persisted identifier contracts. `TeaVmPortabilityGovernanceTest` rejects provider discovery or class-loader lookup from production core sources. The complete TeaVM build and packaged-worker parity test remain the qualification gate; Java compilation by itself is not.

**Closure checks**

- On the JVM and in the worker, use the same case with a simulated clock and
  storage. Check position, revision, allowed actions, and stale reply rejection.
- In native sanity, verify one resumption and the displayed order.
- On mobile, measure cold worker startup and time to the first analysis ply
  separately at the same budget. Test cancellation and slot changes during calculation.

<details>
<summary>Screen reader speech and focus</summary>

**Game Review · Web.** NVDA 2026.3.1 repeated the position and score in RO in the previous release. The graph now uses one accessible value with position and score. The component covers repeated updates, and Extent rejects a Web receipt with a duplicate description. Confirm speech on the corrected build in RO and EN. Keep focus on the graph between two plies with different scores and verify it returns to the trigger after **Why?**. [Screen reader](../../web/README.md#screen-readers-in-game-review)

</details>

## WEB-02 · Adapters and Shared Layout

**P2 · Use compact landscape width more effectively.** At 740 × 420 dp and
font 1.3, the mating source and replay both measure 285 native pixels at
320 dpi (142.5 dp). Navigation fits without page scrolling, but the vertical
stack leaves substantial unused width. Explore a shared landscape arrangement
with explanation beside the board, keeping source/replay sizing aligned and
48 dp controls. Require RO/EN component bounds and native captures before
changing the current no-jump contract.
[Puzzle explanation controls](../../atf-tests/src/test/java/ro/atf/chess/PuzzleExplainLayoutTest.java)

**P2 · Native menu run identity.** Propagate one generated run ID through the Saved/Finished menu instrumentation, execution receipts and Extent. The current focused observations passed with the qualified source and APK, but lack this binding and therefore remain outside strict supplemental qualification. Add negative receipt cases for missing and substituted run IDs before admitting them to that gate.

**P2 · Legacy analysis history oracles.** `awaitHiddenAnalysis` still checks
one history child per ply. Align `analysis-prepare/restore` and their compact
variants with the shared single-move game cursor. Preserve the Analysis
board's full history list and its separate oracle. Keep completed hint/score
readiness and the displayed position as independent checks; a timeout must
not stand in for a wrong UI oracle.
[Current cursor](../../chess-core/src/main/java/ro/atf/chess/MatchHistorySelection.java)

**P2 · Native compact accessibility readiness.** The legacy compact replay stops at a visible Game Review command because its accessibility-node snapshot reports it unavailable. Retain that failed observation; refresh and verify the actual node before any click, without increasing the timeout. Game Review at 360 × 640 dp and 1.3× text, and the separate RO/EN rotation probe, passed on the same signed APK. These checks do not close the legacy helper's failure. Web Openings at 740 × 420 still needs about 37 CSS pixels of document scrolling; keep it in the compact-landscape work above.

[Shared policy, callbacks and rendered checks](../testing/README.md#shared-screen-chrome) own the implemented header/navigation contract; keep only unmet geometry, lifecycle and input criteria here.

## WEB-03 · Component and E2E Tests for the Shared Contract

**Verified locally 2026-11-22 · Saved/Finished browser flow.** A real Chromium probe found that opening a migrated AI game in Saved games raised `ReferenceError: levels is not defined` and left the list empty. The row now resolves the level through the localized menu copy. A browser-independent component checks all three levels in RO/EN and rejects a malformed level. The one-flow Playwright Extent proof checks list separation, mate, reload, archived board fit, and Analysis board history and geometry; screenshots and actions are embedded. This closes the Saved/Finished Web control for the tested compact English profile, not the paired APK/Web 130% rendering matrix below. [Test strategy](../testing/README.md#saved-games-archive-and-continuation)

**Packaging boundary.** Chess consumes the released J0 Reporting and Test Kit JAR/POM pairs. J1's `atf-ui` and `atf-debug-ui` are source modules, so copying an unqualified JAR into Chess would conceal provenance. If a reusable browser adapter is needed after this local proof, request a versioned binary and consumer acceptance from the J1 owner; if a neutral Reporting contract fails, file a separate J0 owner case with a failing report and exact coordinate.

**P2 · Paired adapter evidence.** Shared chrome and the single-move cursor already have common component cases. The gap is a reusable rendered receipt for the same source position, variant, cursor and Back sequence on APK and Web. The retained older Web engine must not be presented as parity with a newer native engine.

**P2 · Rendered seats and captures.** The shared opponent identity, capture
projection, Web painter and fixed seat rows pass JVM, mocked DOM and packaged
worker checks, including history, flip and reload. The legal capture sequence
at plies 13–16 is in `match-capture-rows.json`; the owner contracts and test
names are in the [AI match guide](../../web/README.md#ai-match) and
[test strategy](../testing/README.md#analysis-in-the-ai-game). The
[publication request](../../web/publication.request) identifies the qualified
bundle; byte identity and component assertions do not establish visual parity.

**Analysis board and lists, 2026-11-22.** The study board now projects the same
capture rows as the AI match at its selected ply on both adapters. The two
ten-entry menus use one `MatchStore` and `MatchListEntry` contract; component
checks cross the eleventh active game and result after serialization. Keep the
paired rendered APK/Web receipt open for compact and wide capture-row fit,
including study history and orientation. Web's separate hint replay deliberately
hides study captures so it cannot present them as belonging to another board.

Close only with paired captures from the qualified APK and served Web bundle
at compact 130% text and a wide window. Check each name, level, captured
piece, board edge and menu without clipping or page scrolling. Bind both
captures to source and artifact identities. The MCP browser currently fails
before opening the tab, so this rendered receipt remains open.

- Use the opening-history and mating-review cases from the [shared testing contract](../testing/README.md#shared-screen-chrome).
- Keep source/artifact identities, RO/EN profile, expected state and observed return per adapter. Worker and geometry checks cannot replace missing native or MCP rendering.
- Close only after the paired receipts show the same line and restored state, including reload/restart, on the qualified APK and served Web bundle.

## WEB-04 · Qualification and Publication

**P2 · Release qualification.** In RAG, tie the commit, catalogs, and APK and Web hashes to receipts from the same release. [Update criterion](../testing/README.md#updating-the-web-bundle)

**Closure criterion.** Start the check from **Play in your browser**, below
the APK in the README, and confirm the manifest of the served bundle. On
upgrade, verify the game library, an archived review, and the resumed ply,
including after a reload and in an already open tab.
[Web upgrade](../../web/README.md#explanations-and-upgrade-progress) ·
[Release process](../release/README.md#qualification-and-publication)

## WEB-05 · Menus and Anchored Options

**P2 · Focus, large text and remaining contexts.** The published compact Analysis board has an observed anchored menu and unchanged board without document scrolling. This does not complete the RO/EN 130% matrix for Puzzles, Game Review and Why.

Verify the last option, keyboard dismissal, focus return and the preserved source/cursor in those contexts at compact and desktop sizes. Retain a driver receipt and captures for each missing profile; no full rendered-parity claim follows from the existing component verdict. [Observed compact baseline](https://github.com/crististefanita/chess/blob/b51e17e7752cc0c0a3ec7be9500a362f4d3b20d2/release/0.16.1/reports/web-rendered-observation.json) · [Menu contract](../../web/README.md#main-menu-and-pieces)

## WEB-06 · Game Review color, result action and analysis reuse

**Components pass; paired rendered qualification remains open.** Web assigns the APK's grade-color groups to each move row and shows the localized **📊 Game review** action at the final position. Reopening an already analyzed ply in the same unchanged match reuses only complete, proof-carrying analysis. The cache retains the source position object for playable-line identity, resets the hint cursor on reuse, holds at most 64 positions, and clears when the match checkpoint changes. Incomplete analysis and work from another match are not reused. The cache is process-local; a reload may calculate again. `MatchAnalysisCacheComponentTest` and `ChessWebReviewPresentationTest` qualify the cache and presentation contracts with component assertions and Extent; `NavigationGovernanceTest` verifies the Android and Web adapters use the same cache contract.

`PortableSha256` resolves the TeaVM compilation blocker tracked above. The full TeaVM bundle and packaged-worker parity gates now pass. The cache and presentation changes still need paired APK/Web rendered receipts for a finished result in RO/EN and history replay; shared-source and worker parity do not establish visual parity by themselves.

- The component gate covers every grade group, textual labels, final-only visibility, blockers during analysis, a repeat hit for the same checkpoint/ply, exact-position line replay, hint reset, 64-entry LRU eviction, checkpoint invalidation, and rejection of incomplete work.
- Close only with paired APK/Web receipts for a finished result and colored move list in RO/EN, plus an observed back/forward sequence showing that revisiting a cached AI ply returns immediately without a new search. Do not infer rendered parity from shared source alone.
