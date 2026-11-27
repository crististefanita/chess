# Training-060: preserve the pawn race and demonstrate the payoff

[Tactical curriculum](../board/quest-curation.md) · [Original independent diagnosis](hard-continuation-copies.md#genuine-regression-versus-curriculum-mismatch)

## Corrected behavior

`training-060` keeps its ID, original source FEN, `Kxf2` key, difficulty slot and saved awards. The former three-ply payoff ended with greedy `Kxg2`, which loses according to the retained unrestricted Stockfish H17/H19 comparisons. The correction uses the original seven-ply Lichess solution, `Kxf2 b5 e4 b6 e3 b7 e2`, followed by a separately reviewed twenty-ply extension. That extension is J7 curation; it is not attributed to Lichess.

Both queens promote before the lesson finishes. White's promotion temporarily reverses the visible material count; Black's promotion restores only a one-pawn surplus. The playable lesson therefore continues through the queen ending to `...Kg3`, where Black has an actual **+200 centipawn material gain**. That gain survives every immediate legal White reply, and those replies still leave a playable position. The general closed-material objective remains unchanged.

The line has 27 plies / 14 solver decisions. Each correct intermediate move schedules the retained legal defense and keeps Next locked. The promotion at solver stage five cannot award an early completion. The final explanation replays the real promotions and resulting material rather than suggesting that the temporary queen alone proves the gain.

## Source and teacher evidence

The [original Lichess 0470w response](https://lichess.org/api/puzzle/0470w), its full source PGN and independently replayed UCI history remain pinned in test resources. Replaying all 71 source plies reaches the unchanged catalog FEN, including its clocks. The seven original puzzle moves remain an exact prefix.

The new extension was discovered with unrestricted Stockfish 19. After freezing it, an independent repeat audited **all 27 actual moves at H17 and H19**, each against free legal replies at the same horizon. All **54 comparisons** retain the free teacher WDL; **58 actual raw phases** cover the free roots plus separate forced roots when needed. Every requested horizon completed. The Black choices retain the teacher-estimated win; White's defenses retain the same estimated result as the free choice. These are completed-horizon engine estimates, not a tablebase proof or a Native Elo improvement.

The portable `hard-lessons/training-060-payoff-proof.json` retains the plan, engine identity, actual histories/clocks, free/forced root roles, exact score/WDL, full PVs, raw UCI, per-phase digests and native completion. Its pin uses parsed JSON semantics so Git LF/CRLF transport preserves the evidence; altered facts remain rejected. Original raw-byte digests stay bound to the external actual receipts. The original losing-reference witness remains immutable; its historical failure is not relabelled as a current accepted answer.

## Exact final-position confirmation

Independent replay reaches the six-piece FEN `8/8/8/K3p3/4q2p/6k1/3Q4/8 w - - 4 50`. A separate request to the authoritative [Lichess Syzygy API](https://tablebase.lichess.ovh/standard?fen=8%2F8%2F8%2FK3p3%2F4q2p%2F6k1%2F3Q4%2F8%20w%20-%20-%204%2050) returns **loss for side-to-move White**, `dtz=-8`, `precise_dtz=-8` and `dtm=-58`. All 25 legal White moves appear in the response and lead to a side-to-move Black win. The first DTZ-optimal defense is `Qc3+`. The quiet clock is four plies, leaving ample room for the next zeroing event; this is a win/loss result rather than a blessed loss.

The [API contract](https://github.com/lichess-org/lila-tablebase#http-api) distinguishes DTZ from distance to mate. Its separate DTZ mainline independently replays 22 legal plies to Black checkmate, with the first zeroing at ply eight and maximum quiet clock eleven. That is a verified DTZ-selected line, not a claim of forced mate in 22 against mate-distance-optimal defense.

`hard-lessons/training-060-final-syzygy.json` pins the actual response and request context by JSON meaning. [Training060TablebaseTest](../../atf-tests/src/test/java/ro/atf/chess/Training060TablebaseTest.java) adds two offline components for the complete FEN/clocks, legal reply inventory, exact category and DTZ, LF/CRLF transport and changed-FEN/category/distance negatives. This confirms only the final six-piece position; the earlier high-material race and its free replies retain their distinct H17/H19 teacher scope. No initial-position tablebase proof or Native Elo gain is inferred.

## Component and upgrade contracts

[Training060PayoffTest](../../atf-tests/src/test/java/ro/atf/chess/Training060PayoffTest.java) checks:

- Stable ID/FEN/key/order and the authentic seven-ply prefix before the labelled extension.
- A legal hint at every solver stage, pending-defense restart and exact next-board alignment.
- Both promotions, no premature solve, the final +200 board and every immediate legal defense.
- Rejection of the former `Kxg2` finish, foreign runs, substituted positions/clocks/moves, missing horizons and invented WDL; equivalent LF/CRLF JSON remains portable.
- Existing solved IDs, the original award exactly once, the unsolved first-stage prefix and safe fallback from an obsolete saved finish.

The full material-catalog tests continue to audit all published payoff lines with the existing +200 threshold. They also retain the independent short-horizon key proofs. No opponent search, coaching provider, global objective, scoring or collection size changes as part of this curation.

Evidence root: `C:\work\sah`; relative collection `.studii/cache-reuse/`. `curriculum-evidence/` retains discovery and frozen teacher qualification; `curriculum-build/` retains external JVM builds, the expected baseline-red reproduction and corrected ATF Extent. APK, browser and Android qualification are outside this task.
