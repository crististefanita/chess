# Openings

## Overview

See [analysis sources and selectable lines](analysis-sources.md) for the Stockfish reference score, native replay estimate and three-line selector contract.

- **Scope.** Practice an annotated catalog line from either side. The Analysis board can also show documented continuations for the exact position.
- **Checks.** `OpeningCatalogTest` checks the data and legal moves, `OpeningLessonTest` checks play and resumption, and `OpeningTheoryTest` checks the theory lookup. `ChessWebOpeningStudyTest` checks the Web controls.

- From the main menu, open **Openings**, choose a line, and play as White or Black. The board is oriented with your pieces at the bottom. The app plays the replies from the lesson and explains the idea behind each move. A legal move from another variation leaves the lesson on the current step.

| Lesson | Position studied | Key idea |
|---|---|---|
| Italian Game | Giuoco Pianissimo, C54 | Support the center with c3 and d3, develop the pieces, and castle. [Chess.com](https://www.chess.com/openings/Italian-Game) |
| Ruy Lopez | Closed Variation with 6.d3, C84 | Put pressure on the c6 knight and reinforce the e4 pawn. [Chess.com](https://www.chess.com/openings/ruy-lopez-opening) · [C84 line in the Lichess catalog](https://github.com/lichess-org/chess-openings/blob/master/c.tsv#L1193) |
| Caro–Kann Defense | Advance, Short Variation, B12 | Develop the bishop before e6, then attack the base of the pawn chain as Black. [Chess.com](https://www.chess.com/openings/Caro-Kann-Defense-Advance-Variation) · [Short Variation in the Lichess catalog](https://github.com/lichess-org/chess-openings/blob/master/b.tsv#L302) |
| Queen's Gambit Declined | Orthodox Defense, D63 | Black supports d5 and develops the kingside pieces. [Chess.com](https://www.chess.com/openings/Queens-Gambit-Declined-Orthodox-Defense) |
| Sicilian Defense | Najdorf Variation, B90 | Trade the c-pawn for the d-pawn and control b5 with a6. [Chess.com](https://www.chess.com/openings/Sicilian-Defense-Open-Najdorf-Variation) |
| Scotch Game | Blackburne Attack, C45 | Open the center quickly and pressure the d4 knight. [Chess.com](https://www.chess.com/openings/Scotch-Game) · [Blackburne line in Lichess](https://github.com/lichess-org/chess-openings/blob/master/c.tsv#L753) |
| French Defense | Advance Variation, C02 | White gains space on e5, and Black attacks the base on d4 with c5. [Chess.com](https://www.chess.com/openings/French-Defense-Advance-Variation) · [6.a3 line in Lichess](https://github.com/lichess-org/chess-openings/blob/master/c.tsv#L60) |
| Scandinavian Defense | Classical Variation, B01 | Develop with tempo against the queen and free the bishop with Bf5. [Classical line in Lichess](https://github.com/lichess-org/chess-openings/blob/master/b.tsv#L95) |
| London System | Against the fianchetto, A48 | Play Bf4 before e3 and develop so both sides can castle. [Chess.com](https://www.chess.com/openings/London-System) · [Be2 line in Lichess](https://github.com/lichess-org/chess-openings/blob/master/a.tsv#L642) |
| English Opening | Four Knights and fianchetto, A29 | Control d5 with the c-pawn and respond to the central break before White castles. [Nb6 line in Lichess](https://github.com/lichess-org/chess-openings/blob/3ba6b20d31b2e6125cb0e6a2e792f400717d9d39/a.tsv#L453) |
| Catalan Opening | Open Catalan, Classical Line, E05 | Develop the bishop on g2 and castle before deciding how to recover the c4 pawn. [Classical line in Lichess](https://github.com/lichess-org/chess-openings/blob/3ba6b20d31b2e6125cb0e6a2e792f400717d9d39/e.tsv#L17) |
| Nimzo-Indian Defense | Classical Variation, Keres Defense, E32 | Black pins the c3 knight, and White prepares to recapture with the queen to preserve the pawn structure. [Keres line in Lichess](https://github.com/lichess-org/chess-openings/blob/3ba6b20d31b2e6125cb0e6a2e792f400717d9d39/e.tsv#L169) |
| Alapin Sicilian | Barmen Defense, Modern Line, B22 | White prepares d4 with c3, and Black replies early with d5. [Barmen line in Lichess](https://github.com/lichess-org/chess-openings/blob/3ba6b20d31b2e6125cb0e6a2e792f400717d9d39/b.tsv#L451) |
| King's Indian Defense | Aronin–Taimanov Variation, E97 | Study White's broad center, Black's e5 counterattack, and the closed position after d5. [Aronin–Taimanov line in Lichess](https://github.com/lichess-org/chess-openings/blob/master/e.tsv#L358) |

- The arrows above the board move one ply at a time. History shows the position and explanation for the selected move. Tap the progress indicator to jump to a played position. **Play from here** restarts the lesson from the displayed position. The system **Back** action returns from history to the current position.

- At the current position, the forward arrow plays the exact next move in the lesson. If the catalog reply is pending, tap the arrow again or use **Resume lesson**. If you move a piece on the board, the reply comes automatically. At the end, you can revisit any step or practice again.

- Within the same window, the board keeps its position and size as you step through moves. The progress indicator and buttons reserve their height, and **Lesson hint**, **Resume lesson**, and **Play from here** share the same space.

- **Lesson hint** highlights the piece first. **Show move** selects the piece, draws the arrow, and shows the coordinates and the idea. Tap the marked destination to play the move.

- **AI analysis** evaluates the displayed position, including positions in history and at the end of the lesson. The score, bar and first recommendation appear together from one completed analysis. The monochrome lightbulb uses the AI game’s active green tint and cycles through its available continuations without starting another search. Hiding and showing AI at the same position reuses that result; changing position clears it, and a late result cannot replace the new frame. Lesson guidance keeps its own highlighted move. When the AI suggestion matches your lesson move, you can play it by tapping the destination. A different suggestion is labeled as an AI variation. [AI hint policy](ai-quality.md)

## Lesson configuration

- **Order and identity.** The order in `openings.yaml` sets menu order. Each lesson has a stable ID, an ECO code, an HTTPS source, and a title, a variation, and a plan in Romanian and English.
- **Moves and ideas.** In `line`, each step contains the UCI move and its idea in both languages. For castling, specify the king’s move, such as `e1g1`.
- **Validation.** The engine replays all moves from the starting position and identifies the lesson and step for an illegal move. [YAML catalog](../../chess-core/src/main/resources/openings.yaml)
- **Add a line.** Check the ECO code and moves against the source. Explain what the move changes on the board and what plan it prepares. Preserve the ID when editing the text and add a separate entry for a new variation.
- **Verify the change.** Run the component suite for both colors and the Android check for navigation, replay, and hints. [Lichess ECO dataset](https://github.com/lichess-org/chess-openings) · [Testing strategy](../testing/README.md#openings)

## Opening theory hints on the analysis board

- **Position.** On the [Analysis Board](README.md#analysis-board), hints look for lines from the exact position, including castling and en passant. After every move the app searches the entire catalog for the new position, including after a move outside the previous suggestions.
- **Catalog lines.** The YAML lessons provide up to three lines in catalog order. The count follows availability at that position. Lines with the same first move remain distinct when they continue differently, and each retains all remaining plies and explanations.
- **Replay.** The label below the board opens full-screen replay. Changing the line or step keeps the board and arrows fixed. Each line keeps its own step while the window remains open, and Back returns to the same position. [Game Review replay](game-review.md#review-navigation-and-recommended-lines)
- **Fallback.** Without an applicable YAML lesson, the app uses complete continuations from the ECO index. ECO lines lack the lesson annotations. Without a documented continuation, the lightbulb returns to engine analysis.

## Measured opening references

[OpeningReference](../../chess-core/src/main/java/ro/atf/chess/OpeningReference.java) reads [opening-reference.yaml](../../chess-core/src/main/resources/opening-reference.yaml) for the exact legal lesson prefix. The current 139 unique prefixes cover every catalog frame; 133 retain at least two close roots and 122 retain all three. The key retains the full UCI history, side to move and replayed castling/en-passant rights. The legacy reference reader starts Native analysis for a missing prefix. Calculated coaching in the current Web and Android adapters instead uses the external Lite owner for that exact history. Invalid producer fields, illegal or duplicate lines and inconsistent deltas are rejected during loading.

- **Measured source.** Stockfish 19, depth 16, one thread, 16 MiB hash and MultiPV 3, with a fresh game and cleared hash for every prefix. The YAML records the engine binary digest. These are bounded reference evaluations, not universal scores or a native strength claim.
- **One result.** The bar and up to three legal continuations use the same record and completed depth. Keep only roots within 40 cp of the best. An authored lesson move is not automatically the measured best move. The reference has no native forcing-proof receipt.
- **Opponent variety.** New AI games use the same stored Stockfish roots at an exact covered prefix, rotating pseudo-randomly among the measured options with a saved per-game ordinal. Verified repertoire overrides remain first; uncovered histories keep the current curated-theory and search behavior. No live Stockfish process is started for the opponent move, and old checkpoints retain their deterministic legacy choice. The shared Java implementation and YAML keep Web and APK behavior aligned.
- **Delta.** Store scores from White's perspective: current cp minus the preceding prefix cp; the initial delta is zero. Flipping the board or choosing Black does not reverse this convention. Both adapters label it **Ref. Δ** and expose the producer/depth.
- **Regeneration.** Export actual catalog prefixes with [ExportOpeningReferenceInputs](../../scripts/ExportOpeningReferenceInputs.java), then run [generate-opening-reference.cjs](../../scripts/generate-opening-reference.cjs) with the prefix file, Stockfish 19 binary, output YAML and raw UCI log paths. Retain raw logs outside the checkout. [Component evidence](../testing/README.md#analysis-reuse-components)

## Replay window sizing

The recommendation dialog captures the source board width before opening, through the shared rememberBoardSize adapter. Its frame adds only padding and borders to that square, bounded by the current viewport and the common Analysis-board limit. The fixed information-dialog width no longer shrinks this replay. Tab changes and Next/Previous reuse the captured size; reopening captures the current source again.

When two or three hint choices are available, their buttons share one row and divide the replay width evenly. The text stays on one line and may truncate only when the viewport is narrower than the label; the control remains at least 48 px high. This replay-only rule does not change wrapping in other line collections.

This scope applies only to the recommendation replay. Lesson selection, history and explanation dialogs retain their existing bounds. Narrow or short windows may constrain the square or scroll the dialog when its full readable content cannot fit. [Component checks](../testing/README.md#analysis-reuse-components) · [Sizing adapter](../../web/src/main/web/study-board-size.mjs)
