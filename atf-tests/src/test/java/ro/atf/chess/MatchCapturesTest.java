package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.Level;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchCapturesTest {
    @Test void captureRowsFollowAiMatchHistoryAndBoardOrientation(Checks check) {
        var match = ChessMatch.start(new MatchSetup(Side.WHITE, Level.BASIC, false));
        for (String move : List.of("e2e4", "d7d5", "e4d5", "d8d5")) match = match.play(ChessPosition.Move.fromUci(move));

        var firstCapture = MatchCaptures.at(match, 3, false);
        check.same("The top tray belongs to the Black AI when White is below", Side.BLACK, firstCapture.top().side());
        check.same("The lower tray follows the human White seat", Side.WHITE, firstCapture.bottom().side());
        check.same("The human capture is shown with its material advantage",
                new MatchMaterial.Seat(List.of(new MatchMaterial.Captured(ChessPosition.Kind.PAWN, 1)), 1),
                firstCapture.bottom().material());
        check.same("A future AI capture does not leak into the selected earlier frame",
                new MatchMaterial.Seat(List.of(), 0), firstCapture.top().material());

        var flipped = MatchCaptures.at(match, 3, true);
        check.same("Flipping the board moves the human tray above the board without changing ownership",
                List.of(Side.WHITE, firstCapture.bottom().material()), List.of(flipped.top().side(), flipped.top().material()));
        check.same("Flipping the board moves the AI tray below the board without changing ownership",
                List.of(Side.BLACK, firstCapture.top().material()), List.of(flipped.bottom().side(), flipped.bottom().material()));

        var afterBotCapture = MatchCaptures.at(match, 4, false);
        var pawn = new MatchMaterial.Seat(List.of(new MatchMaterial.Captured(ChessPosition.Kind.PAWN, 1)), 0);
        check.same("The AI capture appears after its reply", pawn, afterBotCapture.top().material());
        check.same("The human's earlier capture remains visible after the AI reply", pawn, afterBotCapture.bottom().material());
        var returned = MatchCaptures.at(ChessMatch.restore(match.checkpoint()), 3, false);
        check.same("Back navigation restores both trays from the exact historical ply", firstCapture, returned);
    }
}