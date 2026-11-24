package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardCandidateCutoffOrderingTest {
    private static HardCandidateSearch engine() {
        return new HardCandidateSearch(p -> TacticalAnalysis.positionValue(p, p.turn()),
                HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY, false, true, true, true, true);
    }

    @Test void learnedOrderingPreservesExhaustiveMinimaxAndSpecialMoves(Checks check) {
        for (String fen : List.of("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2",
                "7k/P7/8/8/8/8/8/7K w - - 0 1", "7k/8/5KQ1/8/8/8/8/8 w - - 0 1")) {
            var context = HardCandidateSearch.Context.fromFen(fen);
            var result = engine().iterative(context, new HardCandidateSearch.Limits(3, 0, 2_000_000, 4_096), () -> false);
            check.same("Ordering never reduces the completed horizon", 3, result.completedDepth());
            check.same("Every legal reply remains represented by minimax", HardCandidateRootPvTest.minimax(context, 3, 0), result.score());
            check.same("The completed full window is exact", HardCandidateSearch.Bound.EXACT, result.bound());
            for (var move : result.line()) { check.same("The retained line is legal", true, context.position().legalMoves().contains(move)); context = context.play(move); }
        }
    }

    @Test void quietRefutationsAndForcingFrontiersAgreeAtTheSameHorizon(Checks check) {
        for (String fen : List.of("6k1/8/3p4/4r3/4Q3/8/8/6K1 w - - 0 1",
                "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2", "7k/P7/8/8/8/8/8/7K w - - 0 1")) {
            var context = HardCandidateSearch.Context.fromFen(fen);
            var limits = new HardCandidateSearch.Limits(3, 8, 2_000_000, 4_096);
            var original = new HardCandidateSearch().fixed(context, 3, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            var ordered = engine(); int score = original.score();
            ordered.fixed(context, 3, limits, score + 1, score + 2);
            ordered.fixed(context, 3, limits, score - 2, score - 1);
            var full = ordered.fixed(context, 3, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            check.same("Both searches complete the identical forcing frontier", HardCandidateSearch.Stop.COMPLETED, full.stop());
            check.same("Narrow-window learning cannot change the later exact score", score, full.score());
            check.same("The later score is an actual full-window result", HardCandidateSearch.Bound.EXACT, full.bound());
        }
    }

    @Test void interruptedLearningCannotLeakAcrossCallsOrHideNodeCost(Checks check) {
        var context = HardMaster.context(ChessMatch.start()); var ordered = engine();
        var limited = ordered.iterative(context, new HardCandidateSearch.Limits(6, 0, 300, 0), () -> false);
        check.same("The test reaches the declared interruption", HardCandidateSearch.Stop.NODE_LIMIT, limited.stop());
        check.same("All work uses the same counter", 300L, limited.nodes());
        check.same("Only the completed exact fallback is published", HardCandidateRootPvTest.minimax(context, limited.completedDepth(), 0), limited.score());
        var limits = new HardCandidateSearch.Limits(3, 0, 100_000, 0);
        check.same("Per-call killers and history reset deterministically", engine().fixed(context, 3, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT),
                ordered.fixed(context, 3, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT));
    }
}
