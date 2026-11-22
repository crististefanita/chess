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

**P2 · Paired adapter evidence.** Shared chrome and the single-move cursor already have common component cases. The gap is a reusable rendered receipt for the same source position, variant, cursor and Back sequence on APK and Web. The retained older Web engine must not be presented as parity with a newer native engine.

**Captured-piece component fix · 2026-11-22.** Web now uses the shared 48-unit silhouette, the APK capture fill and outline colors, and the same 82% icon-to-caption scale. Mocked DOM and Extent component checks cover all five capturable kinds, invalid entries, side ownership and the stable capture rows. This closes the adapter-style mismatch; it does not replace the paired rendered receipt required above.

**Recorded capture sequence · 2026-11-22.** On the API 28 emulator, the
installed 0.15.2 APK showed the AI's two captured white pawns and +1 above the
board, and the human's captured black pawn below it after
`7...Nxe5 8.dxe6 fxe6`. The current capture painter retains those silhouettes,
colors and scale. `match-capture-rows.json` now drives the JVM, mocked DOM and
packaged TeaVM worker at plies 13–16, including return in history, flip and
reload. The focused Extent checks pass. The emulator's 0.15.2 certificate
differs from the signed 0.16.1 APK, so this observation is a capture-painter
reference, not a 0.16.1 release receipt.

**Rendered Web gap remains open.** GitHub Pages currently serves runtime
`1d252b7ada67238062c5f7370d1012ffd342e38d04cb40d1f9d8714c617b512d`,
which has no captured-piece rows. The staged static candidate
`2f50a25b41fffc50c4fb2ae483958ca73e5322fd3a135cb419144954fc6ff9db`
contains them but is not deployed. Browser MCP fails while writing kernel
assets before the tab opens; an attempt to open a browser on the emulator was
rejected by automatic policy review. Recheck the full worker gate below,
deploy the exact static commit through the Pages workflow, then collect an
APK/Web visual comparison on the same captured frame. The current native source
already places match options on the right side of the header, matching Web;
the installed 0.15.2 menu is older.

**Worker gate rechecked · 2026-11-22.** `active-historical-replay` completed in 26 seconds with the unchanged 55-second limit. The full `ChessWebParityPublication` class then exposed a contradictory oracle: it rejected historical root worker/engine aliases that the publishing contract explicitly preserves for older open tabs. The assertion now verifies that legacy aliases form a pair and the current entry loads its worker and engine from the versioned runtime. All six publication tests pass together with Extent. No chess or AI behavior or time limit changed.

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
