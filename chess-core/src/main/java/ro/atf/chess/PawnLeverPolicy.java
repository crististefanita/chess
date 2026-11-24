package ro.atf.chess;

import java.util.List;
import java.util.Map;
import java.util.Set;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

record PawnLeverPolicy(boolean enabled, int reserve, int depth, int extension, int margin) {
    static final PawnLeverPolicy CURRENT = read(ChessYaml.resource("hard-pawn-lever.yaml"));

    private static final int KING_ATTACK_SCALE = 12;

    record Lever(Move push, int leverSquare, int defender, int target) { }

    static PawnLeverPolicy read(Map<?, ?> yaml) {
        if (!yaml.keySet().equals(HardCollections.set("schema", "enabled", "reserve-nodes", "search-plies", "quiescence-plies", "margin-cp"))
                || ChessYaml.number(yaml, "schema") != 1 || !(yaml.get("enabled") instanceof Boolean enabled)) {
            throw new IllegalArgumentException("Invalid pawn lever policy");
        }
        return new PawnLeverPolicy(enabled, bounded(yaml, "reserve-nodes", 1, 15_500), bounded(yaml, "search-plies", 3, 4),
                bounded(yaml, "quiescence-plies", 1, 4), bounded(yaml, "margin-cp", 1, 50));
    }

    Lever find(ChessPosition board, List<TacticalAnalysis.Variation> moves, Move incumbent) {
        if (!enabled || board.isCheck()) return null;
        Side attacker = board.turn();
        int direction = attacker == Side.WHITE ? 8 : -8;
        int king = board.kingSquare(attacker.other());
        if (king / 8 != (attacker == Side.WHITE ? 7 : 0)) return null;
        Lever result = null;
        for (var variation : moves) {
            Move move = variation.move();
            int fromRank = (move.from() / 8) * (direction / 8) + (direction > 0 ? 0 : 7);
            if (move.equals(incumbent) || (fromRank != 3 && fromRank != 4)
                    || move.to() - move.from() != direction || board.pieceAt(move.from()).kind() != Kind.PAWN
                    || board.pieceAt(move.to()) != null || variation.ending() != TacticalAnalysis.Ending.HORIZON) continue;
            int lever = fromRank == 3 ? move.to() + direction : move.to();
            if (board.pieceAt(lever) != null || fromRank == 3 && hasImmediateCapture(board, variation)) continue;
            int file = lever % 8;
            var queen = attackingQueen(board, attacker);
            if (queen < 0 || !rookFilesContested(board, attacker, file, king / 8)) continue;
            for (int offset : new int[]{-1, 1}) {
                int defender = lever + direction + offset;
                int target = defender - direction + offset;
                if (file + 2 * offset < 0 || file + 2 * offset > 7
                        || !pawnAt(board, defender, attacker.other()) || !pawnAt(board, target, attacker.other())
                        || Math.abs(target % 8 - king % 8) > 1 || Math.abs(target / 8 - king / 8) > 2
                        || !board.reachesFrom(queen, target)) continue;
                if (result != null) return null;
                result = new Lever(move, lever, defender, target);
            }
        }
        return result;
    }

    private static int attackingQueen(ChessPosition board, Side side) {
        int queen = -1;
        for (int square = 0; square < 64; square++) {
            var piece = board.pieceAt(square);
            if (piece != null && piece.side() == side && piece.kind() == Kind.QUEEN) {
                if (queen >= 0) return -1;
                queen = square;
            }
        }
        return queen;
    }

    private static boolean rookFilesContested(ChessPosition board, Side side, int leverFile, int homeRank) {
        for (int square = 0; square < 64; square++) {
            var piece = board.pieceAt(square);
            if (piece == null || piece.side() != side || piece.kind() != Kind.ROOK
                    || Math.abs(square % 8 - leverFile) != 1) continue;
            int entry = homeRank * 8 + square % 8;
            if (board.pieceAt(entry) != null || !board.reachesFrom(square, entry)) continue;
            for (int file = 0; file < 8; file++) {
                var defender = board.pieceAt(homeRank * 8 + file);
                if (defender != null && defender.side() != side && defender.kind() == Kind.ROOK
                        && board.reachesFrom(homeRank * 8 + file, entry)) return true;
            }
        }
        return false;
    }

    static boolean congestedPawnFile(ChessPosition board, PawnLeverPolicy.Lever lever) {
        if (lever.push().to() != lever.leverSquare()) return false;
        var turn = board.turn();
        int file = lever.push().to() % 8;
        int step = turn == ChessPosition.Side.WHITE ? 8 : -8;
        int forwardRooks = 0;
        for (int square = lever.push().to() + step; square >= 0 && square < 64; square += step) {
            var piece = board.pieceAt(square);
            if (piece != null && piece.side() != turn && piece.kind() == Kind.ROOK) forwardRooks++;
        }
        if (forwardRooks < 2) return false;
        boolean supportingRook = false;
        boolean flankingRook = false;
        for (int square = 0; square < 64; square++) {
            var piece = board.pieceAt(square);
            if (piece == null || piece.side() != turn || piece.kind() != Kind.ROOK
                    || Integer.signum(square / 8 - lever.push().from() / 8) == Integer.signum(step)) continue;
            if (square % 8 == file) supportingRook = true;
            if (Math.abs(square % 8 - file) == 1) flankingRook = true;
        }
        return supportingRook && flankingRook;
    }

    // Eligibility uses geometric support; the paired search still plays every legal reply.
    static boolean hasProtectedRookEntry(ChessPosition board, Lever lever) {
        if (lever.push().to() != lever.leverSquare()) return false;
        var side = board.turn();
        int home = side == Side.WHITE ? 7 : 0;
        int seventh = side == Side.WHITE ? 6 : 1;
        int king = board.kingSquare(side.other());
        int file = lever.leverSquare() % 8;
        var advanced = board.playGenerated(lever.push());
        int queen = attackingQueen(advanced, side);
        int defenderQueen = attackingQueen(advanced, side.other());
        if (queen < 0 || defenderQueen < 0 || defenderQueen / 8 != seventh
                || Math.abs(defenderQueen % 8 - king % 8) > 3) return false;
        for (int from = 0; from < 64; from++) {
            var piece = advanced.pieceAt(from);
            if (piece == null || piece.side() != side || piece.kind() != Kind.ROOK || Math.abs(from % 8 - file) != 1) continue;
            int entry = BoardSquare.of(from % 8, seventh);
            if (advanced.pieceAt(entry) != null || !advanced.reachesFrom(from, entry)) continue;
            if (!advanced.reachesFrom(queen, entry) && !advanced.reachesFrom(lever.leverSquare(), entry)) continue;
            int step = Integer.signum(defenderQueen - entry);
            if (step == 0) continue;
            boolean blocked = false;
            for (int between = entry + step; between != defenderQueen; between += step) {
                if (advanced.pieceAt(between) != null) { blocked = true; break; }
            }
            if (!blocked && Math.abs(entry % 8 - king % 8) <= 4 && king / 8 == home) return true;
        }
        return false;
    }

    static int kingAttackPressure(ChessPosition position) {
        return kingAttackPressure(position, false);
    }

    /** Optional model: an absolutely pinned piece cannot threaten off its pin axis. */
    static int kingAttackPressure(ChessPosition position, boolean respectPins) {
        int score = 0;
        for (var side : ChessPosition.Side.values()) {
            int king = position.kingSquare(side.other());
            int attack = 0;
            for (int from = 0; from < 64; from++) {
                var piece = position.pieceAt(from);
                if (piece == null || piece.side() != side || piece.kind() == Kind.KING) continue;
                int pinAxis = respectPins ? absolutePinAxis(position, from, side) : 0;
                int hits = 0;
                for (int file = Math.max(0, king % 8 - 1); file <= Math.min(7, king % 8 + 1); file++) {
                    for (int rank = Math.max(0, king / 8 - 1); rank <= Math.min(7, king / 8 + 1); rank++) {
                        int target = BoardSquare.of(file, rank);
                        if (from != target && pinAllows(pinAxis, from, target) && position.reachesFrom(from, target)) hits++;
                    }
                }
                int weight = switch (piece.kind()) {
                    case QUEEN -> 4;
                    case ROOK -> 3;
                    case PAWN -> 1;
                    default -> 2;
                };
                if (hits > 0) attack += weight + hits - 1;
            }
            int amount = attack * attack * KING_ATTACK_SCALE;
            score += position.turn() == side ? amount : -amount;
        }
        return score;
    }

    private static boolean pinAllows(int axis, int from, int target) {
        int file = target % 8 - from % 8, rank = target / 8 - from / 8;
        return switch (axis) { case 0 -> true; case 1 -> file == 0; case 2 -> rank == 0;
            case 3 -> file == rank; case 4 -> file == -rank; default -> throw new IllegalArgumentException("Invalid pin axis"); };
    }

    static boolean queenPinnedToKing(ChessPosition board) {
        return queenPinnedToKing(board, board.turn());
    }

    static boolean queenPinnedToKing(ChessPosition board, Side side) {
        for (int square = 0; square < 64; square++) {
            var piece = board.pieceAt(square);
            if (piece != null && piece.side() == side && piece.kind() == Kind.QUEEN
                    && absolutePinAxis(board, square, piece.side()) != 0) return true;
        }
        return false;
    }

    /** A new legal slider attack may transfer an existing queen pin to a battery. */
    static boolean addsAttackOnPinnedQueen(ChessPosition before, Move move) {
        var mover = before.pieceAt(move.from());
        if (mover == null || mover.side() != before.turn() || move.promotion() != null
                || before.pieceAt(move.to()) != null
                || mover.kind() != Kind.ROOK && mover.kind() != Kind.BISHOP && mover.kind() != Kind.QUEEN) return false;
        var after = before.playGenerated(move);
        if (after.isCheck()) return false;
        Side defender = before.turn().other();
        for (int square = 0; square < 64; square++) {
            var queen = before.pieceAt(square);
            if (queen == null || queen.side() != defender || queen.kind() != Kind.QUEEN
                    || absolutePinAxis(before, square, defender) == 0
                    || absolutePinAxis(after, square, defender) == 0
                    || before.reachesFrom(move.from(), square)
                    || !after.reachesFrom(move.to(), square)) continue;
            // The new attack itself must not expose the attacker's own king.
            if (pinAllows(absolutePinAxis(after, move.to(), mover.side()), move.to(), square)) return true;
        }
        return false;
    }

    private static int absolutePinAxis(ChessPosition board, int from, Side side) {
        int king = board.kingSquare(side);
        int deltaFile = from % 8 - king % 8, deltaRank = from / 8 - king / 8;
        int axis = deltaFile == 0 ? 1 : deltaRank == 0 ? 2 : Math.abs(deltaFile) != Math.abs(deltaRank) ? 0
                : Integer.signum(deltaFile) == Integer.signum(deltaRank) ? 3 : 4;
        if (axis == 0) return 0;
        int fileStep = Integer.signum(deltaFile), rankStep = Integer.signum(deltaRank);
        for (int square = BoardSquare.of(king % 8 + fileStep, king / 8 + rankStep); square != from;
             square = BoardSquare.of(square % 8 + fileStep, square / 8 + rankStep)) {
            if (board.pieceAt(square) != null) return 0;
        }
        for (int file = from % 8 + fileStep, rank = from / 8 + rankStep;
             file >= 0 && file < 8 && rank >= 0 && rank < 8; file += fileStep, rank += rankStep) {
            var piece = board.pieceAt(BoardSquare.of(file, rank));
            if (piece == null) continue;
            return piece.side() != side && (piece.kind() == Kind.QUEEN
                    || axis <= 2 && piece.kind() == Kind.ROOK || axis >= 3 && piece.kind() == Kind.BISHOP) ? axis : 0;
        }
        return 0;
    }

    static boolean shallowTacticalLeader(ChessPosition board, TacticalAnalysis.Variation leader, Lever lever) {
        if (!leader.move().equals(lever.push()) || leader.materialChange() <= 0 || leader.line().size() < 3) return false;
        var afterReply = board.playGenerated(leader.move()).playGenerated(leader.line().get(1));
        var followUp = leader.line().get(2);
        return followUp.from() != lever.push().to() && followUp.to() != lever.defender() && followUp.to() != lever.target()
                && TacticalAnalysis.captureOrder(afterReply, followUp) > 0;
    }
    private static boolean hasImmediateCapture(ChessPosition board, TacticalAnalysis.Variation variation) {
        if (variation.line().size() < 2) return false;
        var afterPush = board.playGenerated(variation.move());
        var reply = variation.line().get(1);
        return TacticalAnalysis.captureOrder(afterPush, reply) > 0;
    }
    List<Move> queenDefenses(ChessPosition advanced, Move pawn) {
        int direction = advanced.turn() == Side.BLACK ? 8 : -8;
        int forward = pawn.to() + direction;
        int rank = forward / 8;
        int file = pawn.to() % 8;
        var defenses = new java.util.ArrayList<Move>();
        for (var reply : advanced.legalMoves()) {
            if (advanced.pieceAt(reply.from()).kind() != Kind.QUEEN) continue;
            var victim = advanced.pieceAt(reply.to());
            if (victim != null && victim.kind() != Kind.PAWN || attacksAdvance(advanced, reply, rank, file)) {
                defenses.add(reply);
            }
        }
        defenses.sort(java.util.Comparator.comparingInt((Move move) -> {
            var victim = advanced.pieceAt(move.to());
            int capture = victim == null ? 0 : 10_000 + TacticalAnalysis.captureOrder(advanced, move);
            int blocksAdvance = move.to() / 8 == rank && Math.abs(move.to() % 8 - file) <= 1 ? 1_000 : 0;
            return capture + blocksAdvance;
        }).reversed());
        return defenses;
    }

    private static boolean attacksAdvance(ChessPosition position, Move reply, int rank, int pawnFile) {
        var defended = position.playGenerated(reply);
        for (int file = Math.max(0, pawnFile - 1); file <= Math.min(7, pawnFile + 1); file++) {
            int target = BoardSquare.of(file, rank);
            if ((reply.to() == target || defended.reachesFrom(reply.to(), target))
                    && !position.reachesFrom(reply.from(), target)) return true;
        }
        return false;
    }

    private static boolean pawnAt(ChessPosition board, int square, Side side) {
        var piece = board.pieceAt(square);
        return piece != null && piece.side() == side && piece.kind() == Kind.PAWN;
    }

    private static int bounded(Map<?, ?> yaml, String key, int min, int max) {
        int value = ChessYaml.number(yaml, key);
        if (value < min || value > max) throw new IllegalArgumentException("Invalid " + key);
        return value;
    }
}
