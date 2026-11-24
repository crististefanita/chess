package ro.atf.chess;

import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Side;

/** Transparent, experimental B features; weights are not production policy. */
final class HardCandidateEvaluation {
    record Weights(int shelter, int development, int isolatedPawn) {
        Weights {
            if (shelter < 0 || shelter > 100 || development < 0 || development > 100
                    || isolatedPawn < 0 || isolatedPawn > 100) throw new IllegalArgumentException("Invalid B weights");
        }
    }
    record Features(int shelter, int development, int isolation) {
        int weighted(Weights weights) {
            return shelter * weights.shelter() + development * weights.development()
                    - isolation * weights.isolatedPawn();
        }
    }
    static final Weights ZERO = new Weights(0, 0, 0);
    static final Weights INITIAL = new Weights(8, 8, 12);
    private HardCandidateEvaluation() { }

    static int delta(ChessPosition position, Weights weights) {
        if (weights.equals(ZERO)) return 0;
        var ours = features(position, position.turn());
        var theirs = features(position, position.turn().other());
        return ours.weighted(weights) - theirs.weighted(weights);
    }

    static Features features(ChessPosition position, Side side) {
        int[] files = new int[8];
        boolean enemyQueen = false;
        int king = -1;
        int phase = 0;
        int developed = 0;
        for (int square = 0; square < 64; square++) {
            var piece = position.pieceAt(square);
            if (piece == null) continue;
            if (piece.kind() != Kind.PAWN && piece.kind() != Kind.KING) phase++;
            if (piece.side() != side) {
                if (piece.kind() == Kind.QUEEN) enemyQueen = true;
                continue;
            }
            if (piece.kind() == Kind.KING) king = square;
            if (piece.kind() == Kind.PAWN) files[square % 8]++;
            int rank = side == Side.WHITE ? square / 8 : 7 - square / 8;
            if ((piece.kind() == Kind.BISHOP || piece.kind() == Kind.KNIGHT) && rank > 0) developed++;
        }
        int isolated = 0;
        for (int file = 0; file < 8; file++) {
            if ((file == 0 || files[file - 1] == 0) && (file == 7 || files[file + 1] == 0)) isolated += files[file];
        }
        int shelter = 0;
        if (enemyQueen && phase >= 6 && king >= 0) {
            for (int file = Math.max(0, king % 8 - 1); file <= Math.min(7, king % 8 + 1); file++) {
                for (int step = 1; step <= 2; step++) {
                    int rank = king / 8 + (side == Side.WHITE ? step : -step);
                    if (rank < 0 || rank > 7) continue;
                    var piece = position.pieceAt(rank * 8 + file);
                    if (piece != null && piece.side() == side && piece.kind() == Kind.PAWN) {
                        shelter++;
                        break;
                    }
                }
            }
        }
        return new Features(shelter, phase >= 10 ? developed : 0, isolated);
    }
}
