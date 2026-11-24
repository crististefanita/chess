package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardCandidateMoveHintTest {
    @Test void aBoardMoveCanCrossRealHistoriesWhileItsNumericScoreCannot(Checks check) {
        var fresh = HardMaster.context(ChessMatch.start());
        var replay = fresh;
        for (String move : List.of("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")) {
            replay = replay.play(ChessPosition.Move.fromUci(move));
        }
        var engine = new HardCandidateSearch(p -> TacticalAnalysis.positionValue(p, p.turn()),
                HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY, false, false, true, true, true);
        var limits = new HardCandidateSearch.Limits(3, 0, 500_000, 20_000);
        engine.fixed(fresh, 2, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        var result = engine.fixed(replay, 1, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("The board-only move is actually reused across different legal histories", true, result.moveHintHits() > 0);
        check.same("The old history's numeric score is not reused", 0L, result.tableHits());
        check.same("The new history retains its independent minimax value", HardCandidateRootPvTest.minimax(replay, 1, 0), result.score());
        check.same("Move hints and score entries share the single finite capacity", true, engine.cachedEntries() <= limits.tableEntries());
    }

    @Test void crossHorizonMovesCannotBorrowScoresAcrossDrawClocksOrLegalHistories(Checks check) {
        String fen = "4k3/8/8/8/8/8/R7/4K3 w - - 0 1";
        var root = HardCandidateSearch.Context.fromFen(fen);
        var engine = new HardCandidateSearch(p -> 73, HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY, false, true, true, true);
        var limits = new HardCandidateSearch.Limits(3, 0, 100_000, 4_096);
        check.same("The fresh reversible ending has its exact static horizon value", 73,
                engine.fixed(root, 2, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
        var boundary = HardCandidateSearch.Context.fromFen(fen.replace("0 1", "149 1"));
        check.same("The same board at the 75-move boundary cannot borrow the fresh score", 0,
                engine.fixed(boundary, 2, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());

        var game = ChessMatch.fromPosition(root.position().play(ChessPosition.Move.fromUci("a2a3")));
        for (int cycle = 0; cycle < 3; cycle++) for (String move : List.of("e8e7", "a3a2", "e7e8", "a2a3")) {
            game = game.play(ChessPosition.Move.fromUci(move));
        }
        for (String move : List.of("e8e7", "a3a2", "e7e8")) game = game.play(ChessPosition.Move.fromUci(move));
        check.same("The legal source is still active", ChessMatch.Result.IN_PROGRESS, game.result());
        check.same("One available move reaches its fifth actual occurrence", ChessMatch.Result.REPETITION_DRAW,
                game.play(ChessPosition.Move.fromUci("a2a3")).result());
        var history = HardMaster.context(game);
        var sameClockWithoutPast = new HardCandidateSearch.Context(history.position(), history.quietPlies(), List.of(history.position().searchKey()));
        check.same("Without the past, every one-ply leaf has the constant opponent value", -73,
                engine.fixed(sameClockWithoutPast, 1, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
        check.same("With identical board and clock, the real history preserves the draw", 0,
                engine.fixed(history, 1, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
    }

    private static HardCandidateSearch engine() {
        return new HardCandidateSearch(p -> TacticalAnalysis.positionValue(p, p.turn()),
                HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY, false, true, true, true);
    }

    @Test void deeperIterationsReuseMovesWithoutBorrowingTheirScores(Checks check) {
        var context = HardMaster.context(ChessMatch.start());
        var engine = engine();
        var result = engine.iterative(context, new HardCandidateSearch.Limits(3, 0, 500_000, 20_000), () -> false);
        check.same("The cached ordering still completes the requested horizon", 3, result.completedDepth());
        check.same("The cache changes ordering, not the exhaustive value", HardCandidateRootPvTest.minimax(context, 3, 0), result.score());
        check.same("Move hints are actually consumed between iterations", true, result.moveHintHits() > 0);
        check.same("Scores and ordering entries share the declared finite capacity", true, engine.cachedEntries() <= 20_000);
    }

    @Test void anOrderingEntryCannotMasqueradeAsAStaticOrExactScore(Checks check) {
        var context = HardMaster.context(ChessMatch.start());
        var engine = new HardCandidateSearch(p -> 73, HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY, false, true, true, true);
        engine.iterative(context, new HardCandidateSearch.Limits(2, 0, 100_000, 20_000), () -> false);
        var staticResult = engine.fixed(context, 0, new HardCandidateSearch.Limits(2, 0, 100_000, 20_000),
                -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("A move-only entry carries no transferable evaluation", 73, staticResult.score());
        var full = engine.fixed(context, 2, new HardCandidateSearch.Limits(2, 0, 100_000, 20_000),
                -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("Warm move hints cannot turn into a fake exact score", 73, full.score());
        for (int capacity : List.of(0, 1, 3)) {
            var limited = engine.fixed(context, 2, new HardCandidateSearch.Limits(2, 0, 100_000, capacity),
                    -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            check.same("Eviction and disabled cache preserve the value", 73, limited.score());
            check.same("The actual table respects even a tiny common capacity", true, engine.cachedEntries() <= capacity);
        }
    }

    @Test void narrowBoundsAndSpecialMovesRetainTheSameFullHorizonValues(Checks check) {
        for (String fen : List.of("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
                "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2", "7k/P7/8/8/8/8/8/7K w - - 0 1")) {
            var context = HardCandidateSearch.Context.fromFen(fen); int expected = HardCandidateRootPvTest.minimax(context, 3, 0);
            var limits = new HardCandidateSearch.Limits(3, 0, 1_000_000, 4_096); var engine = engine();
            engine.fixed(context, 3, limits, expected + 1, expected + 2);
            engine.fixed(context, 3, limits, expected - 2, expected - 1);
            var result = engine.fixed(context, 3, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            check.same("Ordering-only reuse after both bounds preserves exact minimax", expected, result.score());
            check.same("The full window remains exact", HardCandidateSearch.Bound.EXACT, result.bound());
        }
    }
}
