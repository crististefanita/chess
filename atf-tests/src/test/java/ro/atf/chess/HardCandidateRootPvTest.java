package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardCandidateRootPvTest {
    private static HardCandidateSearch engine() {
        return new HardCandidateSearch(p -> TacticalAnalysis.positionValue(p, p.turn()),
                HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY, false, true);
    }

    @Test void reusingTheLastCompletedRootMovePreservesExhaustiveMinimax(Checks check) {
        for (String fen : List.of("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2",
                "7k/P7/8/8/8/8/8/7K w - - 0 1", "7k/8/5KQ1/8/8/8/8/8 w - - 0 1")) {
            var context = HardCandidateSearch.Context.fromFen(fen);
            var limits = new HardCandidateSearch.Limits(3, 0, 2_000_000, 4_096);
            var result = engine().iterative(context, limits, () -> false);
            check.same("PV ordering changes neither horizon nor exactness", HardCandidateSearch.Stop.COMPLETED, result.stop());
            check.same("The iterative root remains exact", HardCandidateSearch.Bound.EXACT, result.bound());
            check.same("The changed move ordering equals an independent exhaustive recurrence", minimax(context, 3, 0), result.score());
            var board = context.position();
            for (var move : result.line()) { check.same("Every retained PV move is legal", true, board.legalMoves().contains(move)); board = board.play(move); }
        }
    }

    @Test void anInterruptedIterationKeepsAnExactCompletedFallbackWithinTheSameAllowance(Checks check) {
        var context = HardMaster.context(ChessMatch.start());
        var result = engine().iterative(context, new HardCandidateSearch.Limits(6, 0, 300, 0), () -> false);
        check.same("The test actually interrupts a deeper iteration", HardCandidateSearch.Stop.NODE_LIMIT, result.stop());
        check.same("Every visit, including discarded deeper work, is charged", 300L, result.nodes());
        check.same("A completed fallback remains available", true, result.hasCompletedIteration() && result.completedDepth() > 0);
        check.same("The fallback is exact at its own completed horizon", minimax(context, result.completedDepth(), 0), result.score());
    }

    @Test void thePreferredMoveCannotLeakIntoAnIndependentFixedSearch(Checks check) {
        var context = HardMaster.context(ChessMatch.start());
        var engine = engine();
        engine.iterative(context, new HardCandidateSearch.Limits(6, 0, 300, 0), () -> false);
        var limits = new HardCandidateSearch.Limits(3, 0, 100_000, 0);
        var reused = engine.fixed(context, 2, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        var fresh = engine().fixed(context, 2, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("Fixed search clears per-call ordering state, including visits and PV tie order", fresh, reused);
    }

    static int minimax(HardCandidateSearch.Context context, int left, int distance) {
        var board = context.position(); var moves = board.legalMoves();
        if (moves.isEmpty()) return board.isCheck() ? -HardCandidateSearch.MATE + distance : 0;
        if (context.automaticDraw() || ChessMatch.fromPosition(board).result() == ChessMatch.Result.MATERIAL_DRAW) return 0;
        if (left == 0) return TacticalAnalysis.positionValue(board, board.turn());
        int best = -HardCandidateSearch.LIMIT;
        for (var move : moves) best = Math.max(best, -minimax(context.play(move), left - 1, distance + 1));
        return best;
    }
}
