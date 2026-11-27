# Training-060: preserve the pawn race and demonstrate the payoff

[Tactical curriculum](../board/quest-curation.md) · [Original independent diagnosis](hard-continuation-copies.md#genuine-regression-versus-curriculum-mismatch)

## Corrected behavior

`training-060` keeps its ID, original source FEN, `Kxf2` key, difficulty slot and saved awards. The former three-ply payoff ended with greedy `Kxg2`, which loses according to the retained unrestricted Stockfish H17/H19 comparisons. The correction uses the original seven-ply Lichess solution, `Kxf2 b5 e4 b6 e3 b7 e2`, followed by a separately reviewed twenty-ply extension. That extension is J7 curation; it is not attributed to Lichess.

Both queens promote before the lesson finishes. White's promotion temporarily reverses the visible material count; Black's promotion restores only a one-pawn surplus. The playable lesson therefore continues through the queen ending to `...Kg3`, where Black has an actual **+200 centipawn material gain**. That gain survives every immediate legal White reply, and those replies still leave a playable position. The general closed-material objective remains unchanged.

The line has 27 plies / 14 solver decisions. Each correct intermediate move schedules the retained legal defense and keeps Next locked. The promotion at solver stage five cannot award an early completion. The final explanation replays the real promotions and resulting material rather than suggesting that the temporary queen alone proves the gain.

## Source and teacher evidence

The [original Lichess 0470w response](https://lichess.org/api/puzzle/0470w), its full source PGN and independently replayed UCI history remain pinned in test resources. Replaying all 71 source plies reaches the unchanged catalog FEN, including its clocks. The seven original puzzle moves remain an exact prefix.

The new extension was discovered with unrestricted Stockfish 19. After freezing it, an independent repeat audited **all 27 actual moves at H17 and H19**, each against free legal replies at the same horizon. All **54 comparisons** retain the free teacher WDL; **58 actual raw phases** cover the free roots plus separate forced roots when needed. Every requested horizon completed. The Black choices retain the teacher-estimated win; White's defenses retain the same estimated result as the free choice. These are completed-horizon engine estimates, not a tablebase proof or a Native Elo improvement.

The portable `hard-lessons/training-060-payoff-proof.json` retains the plan, engine identity, actual histories/clocks, free/forced root roles, exact score/WDL, full PVs, raw UCI, per-phase digests and native completion. The original losing-reference witness remains immutable; its historical failure is not relabelled as a current accepted answer.

## Component and upgrade contracts

[Training060PayoffTest](../../atf-tests/src/test/java/ro/atf/chess/Training060PayoffTest.java) checks:

- Stable ID/FEN/key/order and the authentic seven-ply prefix before the labelled extension.
- A legal hint at every solver stage, pending-defense restart and exact next-board alignment.
- Both promotions, no premature solve, the final +200 board and every immediate legal defense.
- Rejection of the former `Kxg2` finish, foreign runs, substituted positions/clocks/moves, missing horizons and invented WDL.
- Existing solved IDs, the original award exactly once, the unsolved first-stage prefix and safe fallback from an obsolete saved finish.

The full material-catalog tests continue to audit all published payoff lines with the existing +200 threshold. They also retain the independent short-horizon key proofs. No opponent search, coaching provider, global objective, scoring or collection size changes as part of this curation.

Evidence root: `C:\work\sah`; relative collection `.studii/cache-reuse/`. `curriculum-evidence/` retains discovery and frozen teacher qualification; `curriculum-build/` retains external JVM builds, the expected baseline-red reproduction and corrected ATF Extent. APK, browser and Android qualification are outside this task.
