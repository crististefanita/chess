package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Accepted catalog counterexamples: completing a deeper odd horizon must not hide a tactical loss. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class NativeHorizonGuardTest {
    @Test void theObservedQueenCaptureStillHasImmediateLegalRecaptures(Checks check) {
        var lesson = ChessYaml.resource("hard-lessons/visible-exchanges.yaml");
        var example = ChessYaml.map(lesson, "unsafe-tail");
        var board = ChessPosition.fromFen(ChessYaml.string(example, "fen"));
        for (String uci : ChessYaml.string(example, "line").split(" ")) board = board.play(ChessPosition.Move.fromUci(uci));
        check.same("The frontier is still in check after the purported gain", true, board.isCheck());
        check.same("The offered piece is actually the opponent's queen", ChessPosition.Kind.QUEEN,
                board.pieceAt(BoardSquare.parse("f2")).kind());
        for (Object value : (List<?>) example.get("legal-recaptures")) {
            var reply = ChessPosition.Move.fromUci((String) value);
            check.same("The complete position allows the visible recapture " + value, true, board.legalMoves().contains(reply));
            check.same("The legal recapture removes the offered black queen " + value, ChessPosition.Side.WHITE,
                    board.play(reply).pieceAt(BoardSquare.parse("f2")).side());
        }
        check.same("Diagnostic lessons are not automatically admitted as runtime move overrides",
                "diagnostic-only", ChessYaml.string(lesson, "runtime-admission"));
    }

    @Test void thePairedLessonDoesNotRejectASoundQueenOffer(Checks check) {
        var example = ChessYaml.map(ChessYaml.resource("hard-lessons/visible-exchanges.yaml"), "sound-sacrifice");
        var board = ChessPosition.fromFen(ChessYaml.string(example, "fen"));
        var root = ChessPosition.Move.fromUci(ChessYaml.string(example, "root"));
        var proof = CoachingProof.compare(board, List.of(root), 3, Set.of(), new CoachingProof.Policy(1_000_000, 17, 48));
        check.same("The sound offer is verified with free legal opposing replies", true, proof.work().complete());
        check.same("The verified continuation preserves the actual mate", ChessYaml.number(example, "mate-plies"),
                proof.lines().getFirst().signedMatePlies());
        for (var move : proof.lines().getFirst().line()) board = board.play(move);
        check.same("The final position is genuinely checkmate", true, board.isCheckmate());
    }

    @Test void retainsTheRookInvasionFromTraining012(Checks check) {
        verify(check, "4r1k1/1rp2ppp/5q2/2R5/8/P2P4/R2Q1PPP/6K1 b - - 0 24", "b7b1");
    }

    @Test void retainsTheSameRookInvasionWithColorsReversed(Checks check) {
        verify(check, "6k1/r2q1ppp/p2p4/8/2r5/5Q2/1RP2PPP/4R1K1 w - - 0 24", "b2b8");
    }

    private static void verify(Checks check, String fen, String expected) {
        var position = ChessPosition.fromFen(fen);
        var game = ChessMatch.fromPosition(position,
                new MatchSetup(position.turn().other(), MatchSetup.Level.HARD, false));
        var actual = game.analyzeBot(MatchSetup.Level.HARD);
        HardQuestAudit.validate(position, actual);
        check.same("The actual Native game decision preserves the accepted rook tactic", expected,
                actual.decision().move().uci());
        check.same("The reported horizon belongs to the same captured search", actual.decision().depth(),
                actual.search().depth());
        check.same("Additional-node accounting remains explicit", ChessMatch.NodeAccounting.LEGACY_EXTRA,
                actual.decision().accounting());
    }
}
