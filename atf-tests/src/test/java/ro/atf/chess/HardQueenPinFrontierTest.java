package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardQueenPinFrontierTest {
    private static HardCandidateSearch engine(int mode) {
        return new HardCandidateSearch(p -> TacticalAnalysis.positionValue(p, p.turn()),
                HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY, false, true, true, true, true, 0, mode);
    }

    @Test void anAttackedAbsolutelyPinnedQueenCannotPretendTheSidePassedItsTurn(Checks check) {
        for (String fen : List.of("5k2/8/8/8/8/5q2/8/5RK1 b - - 0 1", "5rk1/8/5Q2/8/8/8/8/5K2 w - - 0 1")) {
            var context = HardCandidateSearch.Context.fromFen(fen);
            check.same("The side to move has an actual absolute queen pin", true, PawnLeverPolicy.queenPinnedToKing(context.position()));
            var limits = new HardCandidateSearch.Limits(1, 12, 500_000, 4_096);
            var old = engine(0).fixed(context, 0, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            var proof = engine(3).fixed(context, 0, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            check.same("The historical stand-pat model incorrectly retains a material advantage", true, old.score() > 0);
            check.same("Free legal replies correctly settle the queen-for-rook draw", 0, proof.score());
            check.same("The declared forcing frontier completes", HardCandidateSearch.Stop.COMPLETED, proof.stop());
            check.same("The corrected frontier has an exact finite-horizon value", HardCandidateSearch.Bound.EXACT, proof.bound());
            var game = ChessMatch.fromPosition(context.position());
            for (var move : proof.line()) { check.same("The supporting line is legal", true, game.position().legalMoves().contains(move)); game = game.play(move); }
            check.same("The selected complete defense actually reaches bare kings", ChessMatch.Result.MATERIAL_DRAW, game.result());
        }
    }

    @Test void incompleteQueenPinEvasionsHaveNoFinalScoreAndBoundsStaySeparated(Checks check) {
        var context = HardCandidateSearch.Context.fromFen("5k2/8/8/8/8/5q2/8/5RK1 b - - 0 1");
        var tiny = engine(3).fixed(context, 0, new HardCandidateSearch.Limits(1, 12, 1, 0), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("An interrupted full-reply frontier is explicit", HardCandidateSearch.Stop.NODE_LIMIT, tiny.stop());
        check.same("No stand-pat score escapes from an unfinished queen pin", null, tiny.score());
        var engine = engine(3); var limits = new HardCandidateSearch.Limits(1, 12, 500_000, 4_096);
        engine.fixed(context, 0, limits, 1, 2); engine.fixed(context, 0, limits, -2, -1);
        var exact = engine.fixed(context, 0, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        var off = engine.fixed(context, 0, new HardCandidateSearch.Limits(1, 12, 500_000, 0), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("Both narrow windows preserve the actual draw", 0, exact.score());
        check.same("Cache off independently preserves the same result", 0, off.score());
    }

    @Test void aQueenWithoutTheAbsolutePinDoesNotTriggerTheForcedReplyRule(Checks check) {
        var board = ChessPosition.fromFen("6k1/8/8/8/8/5q2/8/5RK1 b - - 0 1");
        check.same("Moving the king off the rook file is a real counterexample", false, PawnLeverPolicy.queenPinnedToKing(board));
    }
}
