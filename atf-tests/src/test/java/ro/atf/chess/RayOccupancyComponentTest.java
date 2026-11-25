package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class RayOccupancyComponentTest {
    private static final Field OCCUPIED = occupiedField();

    @Test void onlyTheNearestBlockerCanAttackAcrossAllEightDirections(Checks check) {
        for (int file = -1; file <= 1; file++) for (int rank = -1; rank <= 1; rank++) {
            if (file == 0 && rank == 0) continue;
            String label = "ray " + file + "," + rank;
            for (char blocker : new char[]{0, 'N', 'n'}) {
                var board = new char[64];
                board[27] = 'K';
                board[63] = 'k';
                board[27 - 3 * (file + rank * 8)] = 'q';
                if (blocker != 0) board[27 - file - rank * 8] = blocker;
                String fen = fen(board);
                check.same(label + " nearest blocker " + (int) blocker, blocker == 0, ChessPosition.fromFen(fen).isCheck());
                parity(check, label + " blocker " + (int) blocker, fen, 2);
            }
        }
    }

    @Test void signedBit63Bit0AndEmptyRaysRetainTheirMeaning(Checks check) {
        String[] fens = {
                "k6r/8/8/8/8/8/8/7K w - - 0 1",
                "K6k/8/8/8/8/8/8/r7 w - - 0 1",
                "k6q/8/8/8/7n/8/8/7K w - - 0 1",
                "K7/8/8/8/n7/8/8/q6k w - - 0 1",
                "7K/8/8/8/8/8/8/k6q w - - 0 1",
                "7k/8/8/8/8/8/8/K7 w - - 0 1"};
        boolean[] checks = {true, true, false, false, true, false};
        for (int i = 0; i < fens.length; i++) {
            var position = ChessPosition.fromFen(fens[i]);
            check.same("Endpoint fixture " + i + " detects only the nearest attack", checks[i], position.isCheck());
            parity(check, "endpoint " + i, fens[i], 3);
        }
        check.same("A populated h8 sets the signed high bit", true,
                occupied(ChessPosition.fromFen(fens[0])) < 0);
        check.same("A populated a1 sets the low bit", 1L,
                occupied(ChessPosition.fromFen(fens[1])) & 1L);
    }

    @Test void occupancyDeltasFollowTheFinalBoardAfterEverySpecialMove(Checks check) {
        var cases = new ArrayList<>(List.of(
                new Example("white en passant", "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1", "e5d6"),
                new Example("black en passant", "4k3/8/8/8/3Pp3/8/8/4K3 b - d3 0 1", "e4d3"),
                new Example("white short castle", "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1g1"),
                new Example("white long castle", "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1c1"),
                new Example("black short castle clears bit63", "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1", "e8g8"),
                new Example("black long castle", "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1", "e8c8"),
                new Example("king captures onto bit63", "k6r/7K/8/8/8/8/8/8 w - - 0 1", "h7h8"),
                new Example("rook vacates bit63", "4k2r/8/8/8/8/8/8/4K3 b - - 0 1", "h8h2"),
                new Example("king vacates bit63", "7K/8/8/8/8/8/8/k7 w - - 0 1", "h8g8")));
        for (String promotion : List.of("q", "r", "b", "n")) {
            String fen = "7r/6P1/8/7k/8/8/8/K7 w - - 0 1";
            cases.add(new Example("capture promotion onto bit63 " + promotion, fen, "g7h8" + promotion));
            cases.add(new Example("quiet promotion " + promotion, fen, "g7g8" + promotion));
        }
        for (var example : cases) {
            var actual = ChessPosition.fromFen(example.fen());
            var expected = LegalityReferencePosition.fromFen(example.fen());
            long before = occupied(actual);
            var next = actual.play(ChessPosition.Move.fromUci(example.move()));
            var reference = expected.play(LegalityReferencePosition.Move.fromUci(example.move()));
            check.same(example.label() + " preserves the original occupancy", before, occupied(actual));
            check.same(example.label() + " final occupancy equals all actual pieces", boardMask(next), occupied(next));
            check.same(example.label() + " ordered differential tree", referencePerft(reference, 2), tree(next, reference, 2, example.label()));
        }
        // playGenerated is package-private and trusts its caller. Its prior final-board semantics still hold for overlap.
        var actual = ChessPosition.initial().playGenerated(new ChessPosition.Move(12, 12));
        var expected = LegalityReferencePosition.initial().playGenerated(new LegalityReferencePosition.Move(12, 12));
        check.same("An overlapping internal endpoint derives occupancy from the final board", boardMask(actual), occupied(actual));
        check.same("The original internal overlap behavior remains equivalent", referencePerft(expected, 2), tree(actual, expected, 2, "overlap"));
    }

    @Test void enPassantMustRemoveAndReplaceEveryRayBlocker(Checks check) {
        String exposed = "k6r/8/8/6pP/8/8/8/7K w - g6 0 1";
        String blocked = "k6r/8/8/6Pp/8/8/8/7K w - h6 0 1";
        check.rejects("En passant cannot uncover the bit63 rook", IllegalArgumentException.class,
                () -> ChessPosition.fromFen(exposed).play(ChessPosition.Move.fromUci("h5g6")));
        var position = ChessPosition.fromFen(blocked).play(ChessPosition.Move.fromUci("g5h6"));
        check.same("The legal en passant destination replaces the removed blocker",
                new ChessPosition.Piece(ChessPosition.Side.WHITE, ChessPosition.Kind.PAWN), position.pieceAt(BoardSquare.parse("h6")));
        check.same("En passant occupancy matches the resulting board", boardMask(position), occupied(position));
        parity(check, "en passant exposes high-bit ray", exposed, 3);
        parity(check, "en passant keeps high-bit ray blocked", blocked, 3);
    }

    private record Example(String label, String fen, String move) { }

    private static void parity(Checks check, String label, String fen, int depth) {
        var actual = ChessPosition.fromFen(fen);
        var expected = LegalityReferencePosition.fromFen(fen);
        check.same(label + " ordered tree at depth " + depth, referencePerft(expected, depth), tree(actual, expected, depth, label));
    }

    private static long tree(ChessPosition actual, LegalityReferencePosition expected, int depth, String path) {
        require(boardMask(actual) == occupied(actual), "Occupancy: " + path);
        require(actual.isCheck() == expected.isCheck(), "Check: " + path);
        require(actual.searchKey().toString().equals(expected.searchKey().toString()), "Key: " + path);
        for (int square = 0; square < 64; square++) {
            require(String.valueOf(actual.pieceAt(square)).equals(String.valueOf(expected.pieceAt(square))), "Board " + square + ": " + path);
        }
        var moves = actual.legalMoves();
        var references = expected.legalMoves();
        require(moves.toString().equals(references.toString()), "Move order: " + path);
        if (depth == 0) return 1;
        if (depth == 1) return moves.size();
        long count = 0;
        for (int i = 0; i < moves.size(); i++) count += tree(actual.playGenerated(moves.get(i)), expected.playGenerated(references.get(i)),
                depth - 1, path + " " + moves.get(i).uci());
        return count;
    }

    private static long referencePerft(LegalityReferencePosition position, int depth) {
        if (depth == 0) return 1;
        var moves = position.legalMoves();
        if (depth == 1) return moves.size();
        long count = 0;
        for (var move : moves) count += referencePerft(position.playGenerated(move), depth - 1);
        return count;
    }

    private static String fen(char[] board) {
        var result = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                char piece = board[rank * 8 + file];
                if (piece == 0) { empty++; continue; }
                if (empty != 0) { result.append(empty); empty = 0; }
                result.append(piece);
            }
            if (empty != 0) result.append(empty);
            if (rank != 0) result.append('/');
        }
        return result.append(" w - - 0 1").toString();
    }

    private static long boardMask(ChessPosition position) {
        long mask = 0;
        for (int square = 0; square < 64; square++) if (position.pieceAt(square) != null) mask |= 1L << square;
        return mask;
    }

    private static Field occupiedField() {
        try {
            var field = ChessPosition.class.getDeclaredField("occupied");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static long occupied(ChessPosition position) {
        try { return OCCUPIED.getLong(position); }
        catch (IllegalAccessException error) { throw new AssertionError(error); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
