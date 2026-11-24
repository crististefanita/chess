package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardCandidateQuietCheckTest {
    private static HardCandidateSearch engine() {
        return engine(1);
    }
    private static HardCandidateSearch engine(int mode) {
        return new HardCandidateSearch(p -> TacticalAnalysis.positionValue(p, p.turn()),
                HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY, false, true, true, true, true, 0, mode);
    }
    private static int forcing(HardCandidateSearch.Context context, int left, int reserve, int distance) {
        var board = context.position(); var legal = board.legalMoves();
        if (legal.isEmpty()) return board.isCheck() ? -HardCandidateSearch.MATE + distance : 0;
        if (context.automaticDraw() || ChessMatch.fromPosition(board).result() == ChessMatch.Result.MATERIAL_DRAW) return 0;
        boolean check = board.isCheck();
        var captures = board.legalCapturesAndPromotions();
        var moves = check ? legal : legal.stream().filter(move -> captures.contains(move)
                || reserve > 0 && board.play(move).isCheck()).toList();
        int value = check ? -HardCandidateSearch.LIMIT : TacticalAnalysis.positionValue(board, board.turn());
        if (!moves.isEmpty() && left == 0) throw new IllegalStateException("Fixture exceeds its forcing frontier");
        for (var move : moves) value = Math.max(value, -forcing(context.play(move), left - 1,
                reserve - (!check && !captures.contains(move) ? 1 : 0), distance + 1));
        return value;
    }

    @Test void quietCheckingForksAndRepliesMatchIndependentForcingMinimax(Checks check) {
        for (String fen : List.of("4q3/5k2/8/5N2/8/8/8/7K w - - 0 1", "7k/8/5KQ1/8/8/8/8/8 w - - 0 1",
                "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2")) {
            var context = HardCandidateSearch.Context.fromFen(fen);
            var limits = new HardCandidateSearch.Limits(1, 8, 500_000, 4_096);
            int expected = forcing(context, 8, 1, 0);
            var result = engine().fixed(context, 0, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            check.same("The declared checking frontier completes", HardCandidateSearch.Stop.COMPLETED, result.stop());
            check.same("All legal evasions and one optional quiet check agree with exhaustive search", expected, result.score());
            var board = context.position();
            for (var move : result.line()) { check.same("The tactical line remains legal", true, board.legalMoves().contains(move)); board = board.play(move); }
        }
    }

    @Test void quietCheckCostAndIncompleteProofAreNotHidden(Checks check) {
        var context = HardCandidateSearch.Context.fromFen("4q3/5k2/8/5N2/8/8/8/7K w - - 0 1");
        var stopped = engine().fixed(context, 0, new HardCandidateSearch.Limits(1, 8, 1, 0), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("The quiet check is charged to the same exhausted cap", 1L, stopped.nodes());
        check.same("An unresolved checking tree has no numeric score", null, stopped.score());
        check.same("The interruption is explicit", HardCandidateSearch.Stop.NODE_LIMIT, stopped.stop());
        var complete = engine().fixed(context, 0, new HardCandidateSearch.Limits(1, 8, 500_000, 0), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("Checking-fork verification improves on the incomplete static threat view", true,
                complete.score() > TacticalAnalysis.positionValue(context.position(), context.position().turn()));
    }

    @Test void narrowWindowsCannotPoisonTheCheckingFrontier(Checks check) {
        var context = HardCandidateSearch.Context.fromFen("4q3/5k2/8/5N2/8/8/8/7K w - - 0 1");
        int expected = forcing(context, 8, 1, 0); var engine = engine();
        var limits = new HardCandidateSearch.Limits(1, 8, 500_000, 4_096);
        engine.fixed(context, 0, limits, expected + 1, expected + 2);
        engine.fixed(context, 0, limits, expected - 2, expected - 1);
        var warm = engine.fixed(context, 0, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        var off = engine.fixed(context, 0, new HardCandidateSearch.Limits(1, 8, 500_000, 0), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("The warmed checking frontier has the independent exact value", expected, warm.score());
        check.same("The exact bound is preserved", HardCandidateSearch.Bound.EXACT, warm.bound());
        check.same("Cache off has the identical checking horizon", expected, off.score());
    }

    @Test void selectiveCheckingForksRetainTheVerifiedForkAndMateWithoutExpandingUnrelatedChecks(Checks check) {
        for (String fen : List.of("4q3/5k2/8/5N2/8/8/8/7K w - - 0 1", "7k/8/5KQ1/8/8/8/8/8 w - - 0 1")) {
            var context = HardCandidateSearch.Context.fromFen(fen);
            var result = engine(2).fixed(context, 0, new HardCandidateSearch.Limits(1, 8, 500_000, 4_096), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            check.same("The selective frontier retains the independently verified fork or immediate mate", forcing(context, 8, 1, 0), result.score());
        }
        var quiet = HardCandidateSearch.Context.fromFen("7k/p7/6Q1/5K2/8/8/8/8 w - - 0 1");
        check.same("The control actually has no immediate mating move", false,
                quiet.position().legalMoves().stream().anyMatch(move -> quiet.position().play(move).isCheckmate()));
        var result = engine(2).fixed(quiet, 0, new HardCandidateSearch.Limits(1, 8, 500_000, 4_096), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("Quiet checks without a major-piece fork or mate do not expand this selective frontier", List.of(), result.line());
    }
}
