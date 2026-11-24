package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardCandidateCheckExtensionTest {
    private static HardCandidateSearch engine(int reserve) {
        return new HardCandidateSearch(p -> TacticalAnalysis.positionValue(p, p.turn()),
                HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY, false, true, true, true, true, reserve);
    }
    private static int exhaustive(HardCandidateSearch.Context context, int left, int reserve, int distance) {
        var board = context.position(); var legal = board.legalMoves();
        if (legal.isEmpty()) return board.isCheck() ? -HardCandidateSearch.MATE + distance : 0;
        if (context.automaticDraw() || ChessMatch.fromPosition(board).result() == ChessMatch.Result.MATERIAL_DRAW) return 0;
        if (left > 0 && reserve > 0 && board.isCheck()) { left++; reserve--; }
        if (left == 0) return TacticalAnalysis.positionValue(board, board.turn());
        int score = -HardCandidateSearch.LIMIT;
        for (var move : legal) score = Math.max(score, -exhaustive(context.play(move), left - 1, reserve, distance + 1));
        return score;
    }

    @Test void boundedCheckExtensionsMatchAnIndependentExhaustiveRecurrence(Checks check) {
        for (String fen : List.of("4r1k1/8/8/8/8/8/R7/4K3 w - - 0 1", "7k/8/5KQ1/8/8/8/8/8 w - - 0 1",
                "7k/P7/8/8/8/8/8/7K w - - 0 1")) {
            var context = HardCandidateSearch.Context.fromFen(fen);
            for (int reserve = 0; reserve <= 2; reserve++) {
                var result = engine(reserve).fixed(context, 2, new HardCandidateSearch.Limits(2, 0, 2_000_000, 4_096),
                        -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
                check.same("The bounded selective horizon completes", HardCandidateSearch.Stop.COMPLETED, result.stop());
                check.same("Every legal reply is retained by the independent recurrence", exhaustive(context, 2, reserve, 0), result.score());
                check.same("The reported nominal depth remains distinct from extensions", 2, result.completedDepth());
            }
        }
    }

    @Test void boundsAndCacheOffRespectTheRemainingCheckAllowance(Checks check) {
        var context = HardCandidateSearch.Context.fromFen("4r1k1/8/8/8/8/8/R7/4K3 w - - 0 1");
        int exact = exhaustive(context, 2, 2, 0); var engine = engine(2);
        var limits = new HardCandidateSearch.Limits(2, 0, 2_000_000, 4_096);
        engine.fixed(context, 2, limits, exact + 1, exact + 2);
        engine.fixed(context, 2, limits, exact - 2, exact - 1);
        var warm = engine.fixed(context, 2, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        var off = engine.fixed(context, 2, new HardCandidateSearch.Limits(2, 0, 2_000_000, 0), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("Narrow-window entries do not invent an exact result", exact, warm.score());
        check.same("The full window has an exact bound", HardCandidateSearch.Bound.EXACT, warm.bound());
        check.same("Disabled cache has the same selective-horizon score", exact, off.score());
        check.rejects("An unbounded check extension is rejected", IllegalArgumentException.class, () -> engine(3));
    }

    @Test void everyExtensionSharesTheOriginalNodeCap(Checks check) {
        var context = HardCandidateSearch.Context.fromFen("4r1k1/8/8/8/8/8/R7/4K3 w - - 0 1");
        var result = engine(2).iterative(context, new HardCandidateSearch.Limits(8, 0, 500, 0), () -> false);
        check.same("Extensions stop at the same node allowance", 500L, result.nodes());
        check.same("The deeper interrupted search is explicit", HardCandidateSearch.Stop.NODE_LIMIT, result.stop());
        check.same("The completed fallback follows the bounded extension recurrence", exhaustive(context, result.completedDepth(), 2, 0), result.score());
    }
}
