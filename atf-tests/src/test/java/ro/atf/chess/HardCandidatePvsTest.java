package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardCandidatePvsTest {
    private static HardCandidateSearch engine() {
        return new HardCandidateSearch(p -> TacticalAnalysis.positionValue(p, p.turn()),
                HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY, false, true, true);
    }

    @Test void nullWindowScoutingRetainsFullDepthExhaustiveValues(Checks check) {
        for (String fen : List.of("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2",
                "7k/P7/8/8/8/8/8/7K w - - 0 1", "7k/8/5KQ1/8/8/8/8/8 w - - 0 1")) {
            var context = HardCandidateSearch.Context.fromFen(fen);
            var result = engine().iterative(context, new HardCandidateSearch.Limits(3, 0, 2_000_000, 4_096), () -> false);
            check.same("Scouting still completes the full requested horizon", HardCandidateSearch.Stop.COMPLETED, result.stop());
            check.same("A root scout cannot be mislabeled as its final exact score", HardCandidateSearch.Bound.EXACT, result.bound());
            check.same("Full re-search of a promising scout preserves independent minimax", HardCandidateRootPvTest.minimax(context, 3, 0), result.score());
        }
    }

    @Test void scoutingBoundsCannotPoisonFullWindowOrCacheOff(Checks check) {
        var context = HardCandidateSearch.Context.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2");
        var limits = new HardCandidateSearch.Limits(3, 8, 500_000, 4_096);
        var baseline = new HardCandidateSearch().fixed(context, 3, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        var engine = engine(); int score = baseline.score();
        check.same("Fail-low scout remains an upper bound", HardCandidateSearch.Bound.UPPER,
                engine.fixed(context, 3, limits, score + 1, score + 2).bound());
        check.same("Fail-high scout remains a lower bound", HardCandidateSearch.Bound.LOWER,
                engine.fixed(context, 3, limits, score - 2, score - 1).bound());
        var full = engine.fixed(context, 3, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        var off = engine.fixed(context, 3, new HardCandidateSearch.Limits(3, 8, 500_000, 0), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("Both narrow searches preserve the later exact value", score, full.score());
        check.same("The full answer has an exact bound", HardCandidateSearch.Bound.EXACT, full.bound());
        check.same("Cache off agrees at the identical horizon and forcing frontier", score, off.score());
    }

    @Test void everyScoutAndResearchVisitIsChargedBeforePublishingTheFallback(Checks check) {
        var context = HardMaster.context(ChessMatch.start());
        var result = engine().iterative(context, new HardCandidateSearch.Limits(6, 0, 300, 0), () -> false);
        check.same("The bounded experiment actually interrupts", HardCandidateSearch.Stop.NODE_LIMIT, result.stop());
        check.same("Scouts and full re-search share the same allowance", 300L, result.nodes());
        check.same("The published fallback equals exhaustive search at its completed horizon",
                HardCandidateRootPvTest.minimax(context, result.completedDepth(), 0), result.score());
        check.same("An interrupted scout never removes a completed legal move", true,
                !result.line().isEmpty() && context.position().legalMoves().contains(result.line().getFirst()));
    }
}
