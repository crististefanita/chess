package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardKingPressurePinTest {
    @Test void pinnedQueenKeepsOnlyTheContactsAllowedByItsOwnKingsSafety(Checks check) {
        var white = ChessMatch.fromPosition(ChessPosition.fromFen("2r5/1b4k1/2q1p1pr/2p5/1p1pR1QP/pP1P1PP1/P1P5/4R1K1 w - - 1 31"));
        for (String move : List.of("e4e6", "c6f3", "e6e7", "g7f8", "e1f1")) white = white.play(ChessPosition.Move.fromUci(move));
        var pin = white.position();
        check.same("The queen really is absolutely pinned off the file", false, pin.legalMoves().contains(ChessPosition.Move.fromUci("f3g2")));
        check.same("Capturing the pinning rook along the file remains legal", true, pin.legalMoves().contains(ChessPosition.Move.fromUci("f3f1")));
        check.same("An illegal diagonal queen threat cannot inflate the attack model", true,
                PawnLeverPolicy.kingAttackPressure(pin, true) < PawnLeverPolicy.kingAttackPressure(pin));
        var unpinned = pin.play(ChessPosition.Move.fromUci("f8g8"));
        check.same("Moving the king off the pin restores the geometric contacts", PawnLeverPolicy.kingAttackPressure(unpinned),
                PawnLeverPolicy.kingAttackPressure(unpinned, true));

        var black = ChessMatch.fromPosition(ChessPosition.fromFen("4r1k1/p1p5/Pp1p1pp1/1P1Pr1qp/2P5/2Q1P1PR/1B4K1/2R5 b - - 1 31"));
        for (String move : List.of("e5e3", "c3f6", "e3e2", "g2f1", "e8f8")) black = black.play(ChessPosition.Move.fromUci(move));
        check.same("The rule is independent of the piece color", PawnLeverPolicy.kingAttackPressure(pin, true),
                PawnLeverPolicy.kingAttackPressure(black.position(), true));
    }

    @Test void unpinnedOpeningAndSpecialMoveBoardsRetainTheOriginalEvaluation(Checks check) {
        for (String fen : List.of("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2", "7k/P7/8/8/8/8/8/7K w - - 0 1")) {
            var board = ChessPosition.fromFen(fen);
            check.same("Without a pin, the new feature does not change the geometric model", PawnLeverPolicy.kingAttackPressure(board),
                    PawnLeverPolicy.kingAttackPressure(board, true));
        }
    }
}
