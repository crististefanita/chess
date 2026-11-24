package ro.atf.chess;

import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Side;

/** Local king exposure terms for a bounded comparison of queen checks and knight captures. */
final class QueenCounterplay {
    private static final int CHECKING_DISTANCE = 2;
    private static final int QUEEN_PROXIMITY = 95;
    private static final int CHECKING_SQUARE = 44;
    private static final int CHECKING_AT_TWO = 80;
    private static final int SHIELD_PAWN = 15;

    private QueenCounterplay() { }

    static boolean hasQuietApproach(ChessPosition position) {
        int king = position.kingSquare(position.turn().other());
        return position.legalMoves().stream().anyMatch(move -> position.pieceAt(move.from()).kind() == Kind.QUEEN
                && position.pieceAt(move.to()) == null && distance(move.to(), king) == CHECKING_DISTANCE
                && !position.playGenerated(move).isCheck());
    }

    static int bonus(ChessPosition position) {
        return pressure(position, position.turn()) - pressure(position, position.turn().other());
    }

    private static int pressure(ChessPosition position, Side attacker) {
        int queen = -1;
        var defender = attacker.other();
        int king = position.kingSquare(defender);
        int shield = 0;
        for (int square = 0; square < 64; square++) {
            var piece = position.pieceAt(square);
            if (piece == null) continue;
            if (piece.kind() == Kind.QUEEN && piece.side() == attacker) {
                if (queen != -1) return 0;
                queen = square;
            }
            if (piece.kind() == Kind.PAWN && piece.side() == defender && distance(square, king) == 1) shield++;
        }
        if (queen == -1 || shield > 2) return 0;

        // Count geometrically available checks outside the king's immediate reach. The search verifies legal play.
        int checks = 0;
        for (int square = 0; square < 64; square++) {
            if (distance(square, king) != CHECKING_DISTANCE) continue;
            var target = position.pieceAt(square);
            if (target != null && target.side() == attacker) continue;
            if (clearRay(position, queen, square, queen) && clearRay(position, square, king, queen)) checks++;
        }
        int proximity = distance(queen, king) <= CHECKING_DISTANCE ? QUEEN_PROXIMITY : 0;
        return proximity + Math.min(checks, 3) * CHECKING_SQUARE + Math.min(checks, 2) * CHECKING_AT_TWO - shield * SHIELD_PAWN;
    }

    private static boolean clearRay(ChessPosition position, int from, int to, int vacant) {
        int file = to % 8 - from % 8;
        int rank = to / 8 - from / 8;
        if (from == to || (file != 0 && rank != 0 && Math.abs(file) != Math.abs(rank))) return false;
        int step = Integer.signum(file) + 8 * Integer.signum(rank);
        for (int square = from + step; square != to; square += step) {
            if (square != vacant && position.pieceAt(square) != null) return false;
        }
        return true;
    }

    static int distance(int a, int b) {
        return Math.max(Math.abs(a % 8 - b % 8), Math.abs(a / 8 - b / 8));
    }
}
