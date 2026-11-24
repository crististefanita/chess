package ro.atf.chess;
import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardCandidatePinnedAttackerTest {
    @Test void aGeometricAttackOffTheRooksOwnPinAxisDoesNotQualify(Checks check) {
        var before=ChessPosition.fromFen("4r3/3Q1qk1/4R3/8/8/8/8/4K3 w - - 0 1");
        var move=Move.fromUci("e6e7");
        check.same("The rook may approach while staying on its own king's pin axis",true,before.legalMoves().contains(move));
        var after=before.play(move);
        check.same("The rook now geometrically attacks the opposing queen",true,after.reachesFrom(move.to(),BoardSquare.parse("f7")));
        check.same("The enemy queen remains absolutely pinned",true,PawnLeverPolicy.queenPinnedToKing(after));
        var attackerToMove=ChessPosition.fromFen("4r3/3QRqk1/8/8/8/8/8/4K3 w - - 1 1");
        check.same("Actually capturing the queen would expose the attacking side's own king",false,
                attackerToMove.legalMoves().contains(Move.fromUci("e7f7")));
        check.same("Geometric contact is not a new legal threat",false,PawnLeverPolicy.addsAttackOnPinnedQueen(before,move));
    }
}