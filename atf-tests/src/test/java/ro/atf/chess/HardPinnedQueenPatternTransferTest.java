package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardPinnedQueenPatternTransferTest {
    private record Case(String fen, String move, String queen, String offAxis) { }
    private static HardCandidateSearch engine(int mode) {
        return new HardCandidateSearch(p -> 0, HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY,
                false, true, true, true, true, 0, mode);
    }

    @Test void newSliderAttacksTransferAcrossFilesDiagonalsAndColors(Checks check) {
        var cases = List.of(
                new Case("4k3/8/8/4q3/1B6/8/8/4R1K1 w - - 0 1", "b4c3", "e5", "e5d5"),
                new Case("8/7k/8/5q2/R7/8/2B5/6K1 w - - 0 1", "a4f4", "f5", "f5e5"),
                new Case("6k1/2b5/8/r7/5Q2/8/7K/8 b - - 0 1", "a5f5", "f4", "f4e4"));
        for (var example : cases) {
            var before = ChessPosition.fromFen(example.fen());
            var move = Move.fromUci(example.move()); int queen = BoardSquare.parse(example.queen());
            check.same("The transfer candidate is actually legal", true, before.legalMoves().contains(move));
            check.same("The opposing queen has a pre-existing absolute pin", true,
                    PawnLeverPolicy.queenPinnedToKing(before, before.turn().other()));
            String defenderFen = before.turn() == ChessPosition.Side.WHITE
                    ? example.fen().replace(" w ", " b ") : example.fen().replace(" b ", " w ");
            var defenderToMove = ChessPosition.fromFen(defenderFen);
            var offAxis = Move.fromUci(example.offAxis());
            check.same("The synthetic queen escape is geometrically possible before applying king safety", true,
                    defenderToMove.reachesFrom(offAxis.from(), offAxis.to()));
            check.same("Independent legal-move generation rejects that off-axis escape from the pin", false,
                    defenderToMove.legalMoves().contains(offAxis));
            check.same("The moving slider did not already attack that queen", false, before.reachesFrom(move.from(), queen));
            check.same("The candidate is quiet", null, before.pieceAt(move.to()));
            var after = before.play(move);
            check.same("The pin survives the candidate", true, PawnLeverPolicy.queenPinnedToKing(after));
            check.same("The moved slider independently adds the queen attack", true, after.reachesFrom(move.to(), queen));
            var captureNewAttacker = new Move(queen, move.to());
            check.same("The pinned queen could geometrically capture the newly moved slider", true,
                    after.reachesFrom(queen, move.to()));
            check.same("Independent legal move generation forbids that capture because the queen still shields its king", false,
                    after.legalMoves().contains(captureNewAttacker));
            check.same("This is not an undisclosed checking-move extension", false, after.isCheck());
            check.same("The old new-pin-only predicate excludes a pre-existing pin", false,
                    HardCandidateSearch.eligibleQuietQueenPin(before, move));
            check.same("The new legal-attack predicate recognizes this different axis or color", true,
                    PawnLeverPolicy.addsAttackOnPinnedQueen(before, move));
            check.same("Only the explicit new mode admits the transferred pattern", true,
                    engine(7).eligibleQuietPinThreat(before, move));
            check.same("The prior mode is preserved", false, engine(6).eligibleQuietPinThreat(before, move));
        }
    }

    @Test void anOffRayKingOrUnusedQuietSliderDoesNotBecomeATacticalPattern(Checks check) {
        var offRay = ChessPosition.fromFen("7k/8/8/5q2/R7/8/2B5/6K1 w - - 0 1");
        var move = Move.fromUci("a4f4");
        check.same("The off-ray counterexample still has a legal candidate", true, offRay.legalMoves().contains(move));
        check.same("Moving the king off the bishop ray removes the required pin", false,
                PawnLeverPolicy.queenPinnedToKing(offRay, offRay.turn().other()));
        check.same("A geometric queen attack without the pin does not qualify", false,
                PawnLeverPolicy.addsAttackOnPinnedQueen(offRay, move));
        var unrelated = ChessPosition.fromFen("8/7k/8/5q2/R7/8/2B5/6K1 w - - 0 1");
        var idle = Move.fromUci("a4a3");
        check.same("The unused rook move is legal", true, unrelated.legalMoves().contains(idle));
        check.same("The pin persists even though that move adds no queen attack", true,
                PawnLeverPolicy.queenPinnedToKing(unrelated.play(idle)));
        check.same("A persistent pin alone cannot trigger the extension", false,
                engine(7).eligibleQuietPinThreat(unrelated, idle));
    }
}