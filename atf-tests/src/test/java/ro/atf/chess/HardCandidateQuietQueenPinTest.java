package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardCandidateQuietQueenPinTest {
    private static final List<String> FENS = List.of(
            "5k2/8/8/8/8/5q2/8/4R1K1 w - - 0 1",
            "4r1k1/8/5Q2/8/8/8/8/5K2 b - - 0 1");

    private static HardCandidateSearch engine(int mode) {
        return new HardCandidateSearch(HardCandidateQuietQueenPinTest::material,
                HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY, false, true, true, true, true, 0, mode);
    }
    private static int material(ChessPosition board) {
        int result = 0;
        for (int square = 0; square < 64; square++) {
            var piece = board.pieceAt(square); if (piece == null) continue;
            int value = switch (piece.kind()) { case QUEEN -> 900; case ROOK -> 500;
                case BISHOP, KNIGHT -> 300; case PAWN -> 100; case KING -> 0; };
            result += piece.side() == board.turn() ? value : -value;
        }
        return result;
    }
    private static int minimax(ChessPosition board, int left, int distance) {
        var legal = board.legalMoves();
        if (legal.isEmpty()) return board.isCheck() ? -HardCandidateSearch.MATE + distance : 0;
        if (ChessMatch.fromPosition(board).result() == ChessMatch.Result.MATERIAL_DRAW) return 0;
        if (left == 0) return material(board);
        int best = -HardCandidateSearch.LIMIT;
        for (var move : legal) best = Math.max(best, -minimax(board.play(move), left - 1, distance + 1));
        return best;
    }

    @Test void aQuietQueenPinFindsTheActualDrawingDefenseInBothColors(Checks check) {
        for (String fen : FENS) for (int mode : List.of(4, 5, 6, 7)) {
            var context = HardCandidateSearch.Context.fromFen(fen);
            var move = Move.fromUci(context.position().turn() == ChessPosition.Side.WHITE ? "e1f1" : "e8f8");
            var after = context.position().play(move);
            check.same("The new quiet pin is legal", true, context.position().legalMoves().contains(move));
            check.same("The queen blocks the rook line: this move is not check", false, after.isCheck());
            check.same("A separate quiet-pin predicate admits the threat", true,
                    HardCandidateSearch.eligibleQuietQueenPin(context.position(), move));
            var limits = new HardCandidateSearch.Limits(1, 12, 100_000, 4_096);
            var old = engine(3).fixed(context, 0, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            var result = engine(mode).fixed(context, 0, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            check.same("The former capture/check-only frontier misses the saving quiet move", true, old.score() < 0);
            check.same("Three-ply exhaustive minimax independently proves the draw", 0, minimax(context.position(), 3, 0));
            check.same("The new frontier agrees with that complete free-reply proof", 0, result.score());
            check.same("The saving root is not a prescribed engine lookup", move, result.line().getFirst());
            var game = ChessMatch.fromPosition(context.position());
            for (var step : result.line()) game = game.play(step);
            check.same("The chosen representative defense actually reaches bare kings", ChessMatch.Result.MATERIAL_DRAW, game.result());
            check.same("The entire proof stays in the same charged node cap", true, result.nodes() <= limits.nodeLimit());
        }
    }

    @Test void anUnprotectedRookAndAnUnpinnedQueenAreRealCounterexamples(Checks check) {
        var noPin = ChessPosition.fromFen("6k1/8/8/8/8/5q2/8/4R1K1 w - - 0 1");
        check.same("The king is off the rook file, so no absolute pin is created", false,
                HardCandidateSearch.eligibleQuietQueenPin(noPin, Move.fromUci("e1f1")));
        var unsupported = HardCandidateSearch.Context.fromFen("5k2/8/8/8/8/5q2/8/4R2K w - - 0 1");
        for (int mode : List.of(4, 5, 6, 7)) {
        var result = engine(mode).fixed(unsupported, 0, new HardCandidateSearch.Limits(1, 12, 100_000, 4_096),
                -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("Pin geometry alone does not justify a draw when the rook cannot be recaptured", true, result.score() < 0);
        }
    }

    @Test void theRealKingDefenseChangesWhetherTheQuietThreatIsValid(Checks check) {
        var root = ChessPosition.fromFen("2r5/1b4k1/2q1p1pr/2p5/1p1pR1QP/pP1P1PP1/P1P5/4R1K1 w - - 1 31");
        var afterCapture = root.play(Move.fromUci("e4e6")).play(Move.fromUci("c6f3"));
        var checked = afterCapture.play(Move.fromUci("e6e7"));
        check.same("Re7 is the real checking move", true, checked.isCheck());
        var kingOnFile = checked.play(Move.fromUci("g7f8"));
        var rook = Move.fromUci("e1f1");
        check.same("Rf1 is quiet because the queen blocks the rook's line", false, kingOnFile.play(rook).isCheck());
        check.same("Rf1 creates a real pin only with the king on f8", true,
                HardCandidateSearch.eligibleQuietQueenPin(kingOnFile, rook));
        check.same("The full response set must include the king capturing the other rook", true,
                kingOnFile.play(rook).legalMoves().contains(Move.fromUci("f8e7")));
        var kingOffFile = checked.play(Move.fromUci("g7h8"));
        check.same("Kh8 invalidates the proposed geometric threat", false,
                HardCandidateSearch.eligibleQuietQueenPin(kingOffFile, rook));
        var punished = kingOffFile.play(rook).play(Move.fromUci("f3g2"));
        check.same("The otherwise identical Rf1 blunder permits Qg2 mate", true, punished.isCheckmate());
    }
    @Test void theOtherKingDefenseRequiresAnOrdinaryQuietCheckNotAQueenPin(Checks check) {
        var frames = List.of(
                "4R3/1b5k/6pr/2p5/1p1p2QP/pP1P1qP1/P1P5/6K1 w - - 1 35",
                "6k1/p1p5/Pp1p1Qp1/1P1P2qp/2P5/6PR/1B5K/4r3 b - - 1 35");
        for (String fen : frames) {
            var position = ChessPosition.fromFen(fen);
            var move = Move.fromUci(position.turn() == ChessPosition.Side.WHITE ? "g4d7" : "g5d2");
            check.same("The free reply frontier receives a legal quiet checking candidate", true, position.legalMoves().contains(move));
            check.same("This candidate captures no piece", null, position.pieceAt(move.to()));
            var next = position.play(move);
            check.same("Qd7 and its color inverse check the king", true, next.isCheck());
            check.same("The previous checking-fork filter excludes the actual forcing idea", false,
                    engine(4).eligibleQuietCheck(position, move));
            check.same("Mode five admits the ordinary check without an opening or FEN lookup", true,
                    engine(5).eligibleQuietCheck(position, move));
            check.same("The ordinary check is not mislabeled as quiet pin creation", false,
                    HardCandidateSearch.eligibleQuietQueenPin(position, move));
            var block = Move.fromUci(position.turn() == ChessPosition.Side.WHITE ? "f3f7" : "f6f2");
            check.same("The defender may interpose the queen; all such replies must remain free", true,
                    next.legalMoves().contains(block));
        }
    }
    @Test void incompleteThreatsHaveNoFinalScoreAndNarrowWindowsDoNotPoisonTheDraw(Checks check) {
        var context = HardCandidateSearch.Context.fromFen(FENS.getFirst());
        var tiny = engine(4).fixed(context, 0, new HardCandidateSearch.Limits(1, 12, 1, 0),
                -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("The quiet threat has to pay for its child", HardCandidateSearch.Stop.NODE_LIMIT, tiny.stop());
        check.same("An incomplete threat never escapes as a final static score", null, tiny.score());
        check.same("An incomplete proof has no fabricated line", List.of(), tiny.line());
        var engine = engine(4); var limits = new HardCandidateSearch.Limits(1, 12, 100_000, 4_096);
        engine.fixed(context, 0, limits, 1, 2); engine.fixed(context, 0, limits, -2, -1);
        var warm = engine.fixed(context, 0, limits, -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        var off = engine(4).fixed(context, 0, new HardCandidateSearch.Limits(1, 12, 100_000, 0),
                -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("A full window retains the exact draw after both bound types", 0, warm.score());
        check.same("Disabling the cache independently retains the same proof", warm.score(), off.score());
        check.same("The full-window value remains exact", HardCandidateSearch.Bound.EXACT, warm.bound());
    }
}