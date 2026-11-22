# APK–Web parity · Chess Lab

## Overview

**Play in your browser** sits below the APK download link in the main README. The Web base URL opens the main menu; a direct route opens its requested screen. Progress stays local to the device. [Main README](../README.md#play)

## Contracts and checks

Use one component case for the initial state, actions, and observations. Match the same outcomes before comparing rendered interfaces.

- **Shared owner:** `chess-core` supplies the engine and catalog. The Android adapter writes to preferences; the Web adapter sends the same commands to the worker and stores its latest confirmed checkpoint.
- **Component checks:** Reject stale commands and test storage write failures, upgrade progress, and saved-game compatibility with controlled inputs.
- **UI sanity:** Keep one minimal Android or Web flow when the contract needs rendering, accessibility, or a real lifecycle event. [Test strategy](../docs/testing/README.md)

| Feature | Shared contract | Evidence |
|---|---|---|
| Main menu | APK and the Web base URL open **Chess Lab**, with **Games**, **Study**, and **Profile** in the same order. | RO/EN labels and groups; navigation with mocked history and storage. [Menu checks](README.md#main-menu-and-pieces) |
| Pieces and colors | Matches and Game Review share the Staunton outlines from `ChessPieceArtwork` and `BoardView` colors. | Rendering component and one move on the Web board. [Piece checks](README.md#main-menu-and-pieces) |
| Local match | Shared engine; both colors can move, and the saved checkpoint reopens. | Castling, en passant, stalemate, promotion, draws; move, restart, resume, and move again. |
| AI match | White, Black, or Random; Basic, Advanced, or Hard. Clear **Let AI play its moves** to control both sides in the same match. | Shared checks for switching, reopening, cancellation, reply revision, and Elo eligibility. [AI reply control](README.md#ai-reply-control) |
| Named match seats | The core names Alex, Mira, and Atlas by AI level; each adapter places the name and captures at the same top or bottom seat after flip and history navigation. | `MatchSeatParityTest`, mocked RO/EN Web labels and geometry, packaged-worker identity, then paired compact and wide rendered receipts. [Seat contract](README.md#ai-match) |
| Captured pieces | Shared `MatchCaptures` projection selects each seat's captures and material advantage for the visible ply and board orientation. Both adapters use the same 48-unit `ChessPieceArtwork` silhouettes; Web capture icons match the APK's silhouette-only palette, outline and 82%-of-caption scale. | One recorded match fixture drives JVM, mocked DOM and packaged-worker checks for plies 13–16, flip and reload. Extent records the frame assertions; rendered parity still requires the served Web bundle and an APK receipt. [AI match](README.md#ai-match) · [Test contract](../docs/testing/README.md#analysis-in-the-ai-game) |
| Analysis in an AI match | Setup enables the left evaluation bar and history lightbulb from the starting position; the checkpoint saves the choice. | JVM and worker compare the same ply. Real RO/EN checks verify the largest usable square, fixed board, visible controls, and replay from 1/2 with reopening at 2/2. [Contract](README.md#analysis-in-an-ai-match) · [Checks](../docs/testing/README.md#analysis-in-the-ai-game) |
| Board orientation | **Flip board** persists in an active slot. In an archive preview the change is temporary. | Compare position and cursor before and after flipping, then orientation after reopening. [Checks](../docs/testing/README.md#board-orientation-in-a-game) |
| Source-sized replay | Calculated and Mating lines capture the source side at opening; steps and branches retain it. Web uses fixed rows and an internal explanation scroller; APK passes measured geometry to `ReplayBoardLayout`. | Components and MCP verify source equality, no overlap and fixed controls at three profiles. Native build and layout components leave the Android visual check separate. [Contract](../docs/board/README.md#source-sized-replay) |
| Web board size | Analysis board supplies one adaptive sizing limit for Puzzles, matches and Openings. Rails share the same width; extra text does not shrink the square again. | Compare each screen with Analysis at the same viewport and text size, then check history, replay and access to controls. [Sizing](README.md#the-board-in-puzzles-and-matches) |
| History and continuation | Active history can replace the slot; an archived ply in Game Review starts a separate match. | Continue from Game Review on Android and Web. |
| Saved and finished matches | `MatchStore` and `MatchArchive` keep ten entries each. ID and checkpoint bind analysis and resume position; eviction removes that entry's data. | Recover both lists after a bundle update. |
| Game Review | A fixed board, graph, and list share the selected position. Browser checks cover graph value, row selection, and focus after **Why?**. | Confirm actual NVDA speech in RO/EN separately. [Web check](README.md#screen-readers-in-game-review) |
| Puzzles and training | Six chapters share the catalog, hints, scoring, and resumption. `QuestPayoff` supplies tactical replay and the verified threat reason. | Components and browser recover Tactics, Tactical training, and In-between move progress. [Upgrade checks](README.md#explanations-and-upgrade-progress) |
| AI match menu | `MatchControls` defines the same actions, draw and resignation outcomes, and archive policy. | Worker checks declined offers and rejects history or stale commands. Browser checks analysis, confirmation, and result retention on reload. [Match options](README.md#ai-match-options) |
| Openings | `OpeningLesson` shares the board and control geometry on compact and landscape profiles at 1.3× text scale. Web uses the native side-panel threshold in compact landscape. | Components bind the breakpoint and board fit; one EN/RO browser flow checks 740 × 420 without document scrolling and with a fixed square through Next/Previous. A signed-APK paired capture remains separate. [Profiles](../docs/testing/README.md#openings) |
| Analysis board | `AnalysisEntry` binds source, ply, and orientation for both active and archived matches. Back restores the source; study keeps its own bookmark. | Shared JVM/worker cases; mock navigation, storage, and focus restoration. [Contract check](../docs/testing/README.md#analysis-board-from-a-game) |
| Profile, language, and presentation | Local profile preserves the match name and RO/EN preference. | Verify larger text, captured pieces, material advantage, keyboard, and native accessibility equivalents. |

The game guides own the product rules and platform behavior. [Matches, puzzles, and analysis](../docs/board/README.md) · [Game Review](../docs/board/game-review.md)

## Publishing the link

1. Build and qualify the bundle in TMP. Keep the exact static tree reachable from an immutable commit or tag; use a separate worktree for the staging files.
2. Preserve previously published `runtime/<SHA-256>/` directories byte for byte and the legacy root URLs used by older open tabs. Include the new runtime directory, `index.html`, `.nojekyll` and the generated `.gitattributes` in the same commit. The `* -text` rule prevents Git from changing qualified bytes; compare staged blobs with the measured bundle before publication.
3. Put the qualified static commit and raw-tree digest in [the publication request](publication.request), then push that file on `main`. Only a change to this file triggers the [Pages workflow](../.github/workflows/chess-pages.yml); it validates the two identities before downloading the static tree. Manual dispatch with the same values remains available. The documentation branch `main_RO` never deploys.
    Compute `manifest_sha256` from the exact static commit. In a POSIX shell run:

    ```bash
    git ls-tree -r --full-tree "$SITE_COMMIT" | sha256sum
    ```

4. Open **Play in your browser**. Verify the declared versioned files and reload with the cache intact. Check checkpoint resumption and offline play after the first load; an open tab may keep the previous runtime until it reloads.
5. Record the source revision, deployed static commit, runtime hash, and Extent receipts. Verify the served bytes independently of the browser cache.

If upload succeeds but GitHub Pages temporarily cannot find its `github-pages`
artifact, keep the validated static commit and request unchanged. Rerun only the
failed deployment job, then verify the served bytes against the qualified site.
Treat a failed retry or a byte mismatch as an open publication failure.

[Bundle structure](README.md#web-bundle-identity) ·
[Test strategy](../docs/testing/README.md#updating-the-web-bundle) ·
[Release process](../docs/release/README.md)
