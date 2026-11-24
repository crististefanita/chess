package ro.atf.chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.SearchKey;

/**
 * Hard A search, selected through the separate application master policy.
 * Fixed-depth alpha-beta is independently testable before selective search is added.
 * Scores are relative to the side to move. Default draws match the application's
 * automatic 75-move/fivefold policy; auto-claiming earlier is an explicit experiment.
 */
final class HardCandidateSearch {
    static final int MATE = 100_000;
    static final int LIMIT = MATE + 1;
    enum Bound { EXACT, LOWER, UPPER }
    enum Stop { COMPLETED, NODE_LIMIT, CANCELLED, QUIESCENCE_LIMIT, MATE_CERTIFICATE }
    enum DrawPolicy { AUTOMATIC_ONLY, CLAIM_AVAILABLE }

    record Context(ChessPosition position, int quietPlies, List<SearchKey> history) {
        Context {
            Objects.requireNonNull(position);
            history = HardCollections.copyList(history);
            if (quietPlies < 0 || history.isEmpty()
                    || !history.get(history.size() - 1).equals(position.searchKey())) {
                throw new IllegalArgumentException("History must end at the searched position");
            }
        }

        static Context fromFen(String fen) {
            var position = ChessPosition.fromFen(fen);
            return new Context(position, Integer.parseInt(fen.trim().split("\\s+")[4]),
                    HardCollections.list(position.searchKey()));
        }

        Context play(Move move) {
            if (!position.legalMoves().contains(move)) throw new IllegalArgumentException("Illegal continuation");
            return afterGenerated(move);
        }

        private Context afterGenerated(Move move) {
            boolean reset = position.pieceAt(move.from()).kind() == Kind.PAWN || position.pieceAt(move.to()) != null;
            var next = position.playGenerated(move);
            var visits = reset ? new ArrayList<SearchKey>() : new ArrayList<>(history);
            visits.add(next.searchKey());
            return new Context(next, reset ? 0 : quietPlies + 1, visits);
        }

        boolean drawClaim() {
            return quietPlies >= 100 || history.stream().filter(position.searchKey()::equals).count() >= 3;
        }

        boolean automaticDraw() {
            return quietPlies >= 150 || history.stream().filter(position.searchKey()::equals).count() >= 5;
        }
    }

    record Limits(int maxDepth, int quiescencePlies, long nodeLimit, int tableEntries) {
        Limits {
            if (maxDepth < 1 || maxDepth > 32 || quiescencePlies < 0 || quiescencePlies > 32
                    || nodeLimit < 1 || tableEntries < 0 || tableEntries > 1_000_000) {
                throw new IllegalArgumentException("Invalid experimental search limits");
            }
        }
    }

    record Result(Integer score, Bound bound, List<Move> line, int completedDepth, long nodes,
                  long tableHits, long moveHintHits, Stop stop) {
        Result { line = HardCollections.copyList(line); }
        Result(Integer score, Bound bound, List<Move> line, int completedDepth, long nodes, long tableHits, Stop stop) {
            this(score, bound, line, completedDepth, nodes, tableHits, 0, stop);
        }
        boolean hasCompletedIteration() { return score != null; }
    }

    private record Key(SearchKey position, int quietPlies, List<SearchKey> history,
                       int depth, int qLeft, int distance, int checkReserve) { }
    private record MoveKey(SearchKey position) { }
    private record MoveHint(int depth, Move move) { }
    private record Node(int score, Bound bound, List<Move> line) {
        Node { line = HardCollections.copyList(line); }
    }
    private static final class Halt extends RuntimeException {
        final Stop reason;
        Halt(Stop reason) { super(null, null, false, false); this.reason = reason; }
    }
    private static final class Work {
        final long limit;
        final BooleanSupplier cancelled;
        long nodes;
        long hits;
        long moveHints;
        Work(long limit, BooleanSupplier cancelled) { this.limit = limit; this.cancelled = cancelled; }
        void visit() {
            if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new Halt(Stop.CANCELLED);
            if (nodes >= limit) throw new Halt(Stop.NODE_LIMIT);
            nodes++;
            HardSearchExperiment.visit();
        }
    }

    private final ToIntFunction<ChessPosition> evaluator;
    private final DrawPolicy drawPolicy;
    private final boolean reserveAcrossDepths;
    private final boolean reuseRootPv;
    private final boolean principalVariationSearch;
    private final boolean reuseMoveHints;
    private final boolean cutoffOrdering;
    private final int checkExtensions;
    private final int quietChecks;
    private final boolean checkingForksOnly;
    private final boolean queenPinEvasions;
    private final boolean queenPinCreation;
    private final boolean reinforceQueenPin;
    private final Move[][] killers = new Move[128][2];
    private final int[][][] quietHistory = new int[2][64][64];
    private Move iterativePreferredRoot;
    private final LinkedHashMap<Key, Node> table = new LinkedHashMap<>(256, 0.75f, true);
    private final LinkedHashMap<Object, Object> sharedTable = new LinkedHashMap<>(256, 0.75f, true);
    private int capacity;
    private Object evaluationScope;
    int cachedEntries() { return reuseMoveHints ? sharedTable.size() : table.size(); }

    HardCandidateSearch() {
        this(position -> HardSearchExperiment.evaluate(position, false, false));
    }

    HardCandidateSearch(ToIntFunction<ChessPosition> evaluator) {
        this(evaluator, DrawPolicy.AUTOMATIC_ONLY);
    }

    HardCandidateSearch(ToIntFunction<ChessPosition> evaluator, DrawPolicy drawPolicy) {
        this(evaluator, drawPolicy, false);
    }

    HardCandidateSearch(ToIntFunction<ChessPosition> evaluator, DrawPolicy drawPolicy, boolean reserveAcrossDepths) {
        this(evaluator, drawPolicy, reserveAcrossDepths, false);
    }

    HardCandidateSearch(ToIntFunction<ChessPosition> evaluator, DrawPolicy drawPolicy, boolean reserveAcrossDepths, boolean reuseRootPv) {
        this(evaluator, drawPolicy, reserveAcrossDepths, reuseRootPv, false);
    }

    HardCandidateSearch(ToIntFunction<ChessPosition> evaluator, DrawPolicy drawPolicy, boolean reserveAcrossDepths,
                        boolean reuseRootPv, boolean principalVariationSearch) {
        this(evaluator, drawPolicy, reserveAcrossDepths, reuseRootPv, principalVariationSearch, false);
    }

    HardCandidateSearch(ToIntFunction<ChessPosition> evaluator, DrawPolicy drawPolicy, boolean reserveAcrossDepths,
                        boolean reuseRootPv, boolean principalVariationSearch, boolean reuseMoveHints) {
        this(evaluator, drawPolicy, reserveAcrossDepths, reuseRootPv, principalVariationSearch, reuseMoveHints, false);
    }

    HardCandidateSearch(ToIntFunction<ChessPosition> evaluator, DrawPolicy drawPolicy, boolean reserveAcrossDepths,
                        boolean reuseRootPv, boolean principalVariationSearch, boolean reuseMoveHints, boolean cutoffOrdering) {
        this(evaluator, drawPolicy, reserveAcrossDepths, reuseRootPv, principalVariationSearch, reuseMoveHints, cutoffOrdering, 0);
    }

    HardCandidateSearch(ToIntFunction<ChessPosition> evaluator, DrawPolicy drawPolicy, boolean reserveAcrossDepths,
                        boolean reuseRootPv, boolean principalVariationSearch, boolean reuseMoveHints, boolean cutoffOrdering, int checkExtensions) {
        this(evaluator, drawPolicy, reserveAcrossDepths, reuseRootPv, principalVariationSearch, reuseMoveHints, cutoffOrdering, checkExtensions, 0);
    }

    HardCandidateSearch(ToIntFunction<ChessPosition> evaluator, DrawPolicy drawPolicy, boolean reserveAcrossDepths,
                        boolean reuseRootPv, boolean principalVariationSearch, boolean reuseMoveHints, boolean cutoffOrdering,
                        int checkExtensions, int quietChecks) {
        this.evaluator = Objects.requireNonNull(evaluator);
        this.drawPolicy = Objects.requireNonNull(drawPolicy);
        this.reserveAcrossDepths = reserveAcrossDepths;
        this.reuseRootPv = reuseRootPv;
        this.principalVariationSearch = principalVariationSearch;
        this.reuseMoveHints = reuseMoveHints;
        this.cutoffOrdering = cutoffOrdering;
        if (checkExtensions < 0 || checkExtensions > 2) throw new IllegalArgumentException("Invalid per-line check reserve");
        this.checkExtensions = checkExtensions;
        if (quietChecks < 0 || quietChecks > 7) throw new IllegalArgumentException("Invalid quiet-check mode");
        this.quietChecks = quietChecks == 0 ? 0 : quietChecks >= 6 ? 2 : 1;
        this.checkingForksOnly = quietChecks >= 2 && quietChecks < 5;
        this.queenPinEvasions = quietChecks >= 3;
        this.queenPinCreation = quietChecks >= 4;
        this.reinforceQueenPin = quietChecks == 7;
    }

    Result iterative(Context context, Limits limits, BooleanSupplier cancelled) {
        return iterativeWithForcingReserve(context, limits, cancelled, limits.quiescencePlies());
    }

    Result iterativeWithForcingReserve(Context context, Limits limits, BooleanSupplier cancelled, int maxForcingPlies) {
        if (maxForcingPlies < limits.quiescencePlies() || maxForcingPlies > 32) {
            throw new IllegalArgumentException("Invalid initial forcing reserve");
        }
        configure(limits.tableEntries());
        resetOrdering();
        iterativePreferredRoot = null;
        var work = new Work(limits.nodeLimit(), cancelled);
        Node completed = null;
        int depth = 0;
        int forcingPlies = limits.quiescencePlies();
        Stop stop = Stop.COMPLETED;
        for (int next = 1; next <= limits.maxDepth();) {
            try {
                var result = search(context, next, forcingPlies, 0, -LIMIT, LIMIT, work, checkExtensions);
                if (result.bound() != Bound.EXACT) throw new IllegalStateException("Full window must finish exact");
                completed = result;
                if (reuseRootPv && !result.line().isEmpty()) iterativePreferredRoot = result.line().get(0);
                depth = next++;
            } catch (Halt halt) {
                // Retry an unresolved initial proof, retaining its full cost and budget.
                // A completed shallower iteration remains the fallback for later interruptions.
                if (halt.reason == Stop.QUIESCENCE_LIMIT && (completed == null || reserveAcrossDepths) && forcingPlies > 0
                        && forcingPlies < maxForcingPlies) {
                    forcingPlies = Math.min(maxForcingPlies, forcingPlies * 2);
                    continue;
                }
                stop = halt.reason;
                break;
            }
        }
        return result(completed, depth, work, stop);
    }

    Result fixed(Context context, int depth, Limits limits, int alpha, int beta) {
        if (depth < 0 || depth > limits.maxDepth() || alpha < -LIMIT || beta > LIMIT || alpha >= beta) {
            throw new IllegalArgumentException("Invalid fixed-depth window");
        }
        configure(limits.tableEntries());
        resetOrdering();
        iterativePreferredRoot = null;
        var work = new Work(limits.nodeLimit(), () -> false);
        try {
            return result(search(context, depth, limits.quiescencePlies(), 0, alpha, beta, work, checkExtensions),
                    depth, work, Stop.COMPLETED);
        } catch (Halt halt) {
            return result(null, 0, work, halt.reason);
        }
    }

    private static Result result(Node node, int depth, Work work, Stop stop) {
        return new Result(node == null ? null : node.score(), node == null ? null : node.bound(),
                node == null ? HardCollections.list() : node.line(), depth, work.nodes, work.hits, work.moveHints, stop);
    }

    private void configure(int entries) {
        Object currentScope = HardSearchExperiment.identity();
        if (currentScope != evaluationScope) {
            table.clear();
            sharedTable.clear();
            evaluationScope = currentScope;
        }
        capacity = entries;
        while (table.size() > capacity) table.remove(table.keySet().iterator().next());
        while (sharedTable.size() > capacity) sharedTable.remove(sharedTable.keySet().iterator().next());
    }

    private Node search(Context context, int depth, int qLeft, int distance, int alpha, int beta, Work work, int checkReserve) {
        work.visit();
        var position = context.position();
        var legal = position.legalMoves();
        if (legal.isEmpty()) return exact(position.isCheck() ? -MATE + distance : 0);
        if (drawn(context) || insufficient(position)) return exact(0);
        if (depth > 0 && checkReserve > 0 && position.isCheck()) { depth++; checkReserve--; }
        var key = new Key(position.searchKey(), context.quietPlies(), context.history(), depth, qLeft, distance, checkReserve);
        Node saved = capacity == 0 ? null : !reuseMoveHints ? table.get(key) : (Node) sharedTable.get(key);
        if (saved != null && (saved.bound() == Bound.EXACT
                || saved.bound() == Bound.LOWER && saved.score() >= beta
                || saved.bound() == Bound.UPPER && saved.score() <= alpha)) {
            work.hits++;
            return saved;
        }
        Node result;
        if (depth == 0 && qLeft == 0) {
            result = exact(evaluate(position));
        } else if (depth == 0) {
            result = quiescence(context, qLeft, distance, alpha, beta, work, legal, quietChecks, queenPinEvasions ? 1 : 0, queenPinCreation ? 1 : 0);
        } else {
            int originalAlpha = alpha;
            Node best = null;
            var preferred = distance == 0 && reuseRootPv && iterativePreferredRoot != null
                    ? iterativePreferredRoot : saved == null || saved.line().isEmpty() ? null : saved.line().get(0);
            if (reuseMoveHints && capacity > 0 && !(distance == 0 && reuseRootPv && iterativePreferredRoot != null)) {
                var hint = (MoveHint) sharedTable.get(new MoveKey(position.searchKey()));
                if (hint != null && legal.contains(hint.move())) {
                    preferred = hint.move();
                    work.moveHints++;
                }
            }
            for (Move move : orderedForSearch(position, legal, preferred, distance)) {
                var next = context.afterGenerated(move);
                Node child;
                if (principalVariationSearch && best != null) {
                    child = search(next, depth - 1, qLeft, distance + 1, -alpha - 1, -alpha, work, checkReserve);
                    int provisional = -child.score();
                    if (provisional > alpha && provisional < beta) {
                        child = search(next, depth - 1, qLeft, distance + 1, -beta, -alpha, work, checkReserve);
                    }
                } else child = search(next, depth - 1, qLeft, distance + 1, -beta, -alpha, work, checkReserve);
                int score = -child.score();
                if (best == null || score > best.score()) best = prepend(move, child, score);
                alpha = Math.max(alpha, score);
                if (alpha >= beta) {
                    if (cutoffOrdering && quiet(position, move)) rewardCutoff(position, move, distance, depth);
                    break;
                }
            }
            result = window(best, originalAlpha, beta);
        }
        remember(key, result);
        if (reuseMoveHints && capacity > 0 && !result.line().isEmpty()) {
            // A board-only move crosses histories safely: it is rechecked for legality
            // and carries no numeric score. Scores retain the complete history key.
            var moveKey = new MoveKey(position.searchKey());
            var oldHint = (MoveHint) sharedTable.get(moveKey);
            if (oldHint == null || depth >= oldHint.depth()) sharedTable.put(moveKey, new MoveHint(depth, result.line().get(0)));
            while (sharedTable.size() > capacity) sharedTable.remove(sharedTable.keySet().iterator().next());
        }
        return result;
    }

    private Node quiescence(Context context, int left, int distance, int alpha, int beta, Work work,
                            List<Move> legal, int quietCheckReserve, int pinEvasionReserve, int pinCreationReserve) {
        var position = context.position();
        boolean check = position.isCheck();
        boolean pinEvasion = !check && pinEvasionReserve > 0 && PawnLeverPolicy.queenPinnedToKing(position);
        boolean forcedReply = check || pinEvasion;
        int originalAlpha = alpha;
        Node best = forcedReply ? null : exact(evaluate(position));
        if (!forcedReply) {
            if (best.score() >= beta) return window(best, originalAlpha, beta);
            alpha = Math.max(alpha, best.score());
        }
        // Legality was already established at this node. Keep en passant and all
        // promotions without regenerating and testing every move a second time.
        var moves = forcedReply ? legal : legal.stream().filter(move -> move.promotion() != null
                || position.pieceAt(move.to()) != null
                || position.pieceAt(move.from()).kind() == Kind.PAWN && move.from() % 8 != move.to() % 8
                || quietCheckReserve > 0 && eligibleQuietCheck(position, move)
                || pinCreationReserve > 0 && pinEvasionReserve > 0 && eligibleQuietPinThreat(position, move)).collect(java.util.stream.Collectors.toList());
        if (moves.isEmpty()) return window(best, originalAlpha, beta);
        // A configured cap is an incomplete proof, never a static score in a forcing position.
        if (left == 0) throw new Halt(Stop.QUIESCENCE_LIMIT);
        for (Move move : ordered(position, moves, null)) {
            boolean usesPinThreat = !forcedReply && pinCreationReserve > 0 && pinEvasionReserve > 0
                    && eligibleQuietPinThreat(position, move);
            var next = context.afterGenerated(move);
            work.visit();
            var nextLegal = next.position().legalMoves();
            Node child;
            if (nextLegal.isEmpty()) child = exact(next.position().isCheck() ? -MATE + distance + 1 : 0);
            else if (drawn(next) || insufficient(next.position())) child = exact(0);
            else child = quiescence(next, left - 1, distance + 1, -beta, -alpha, work, nextLegal,
                    quietCheckReserve - (!forcedReply && quiet(position, move) && !usesPinThreat ? 1 : 0),
                    pinEvasionReserve - (pinEvasion ? 1 : 0), pinCreationReserve - (usesPinThreat ? 1 : 0));
            int score = -child.score();
            if (best == null || score > best.score()) best = prepend(move, child, score);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) break;
        }
        return window(best, originalAlpha, beta);
    }

    private int evaluate(ChessPosition position) {
        int score = evaluator.applyAsInt(position);
        if (Math.abs((long) score) >= MATE - 128) throw new IllegalArgumentException("Static score overlaps mate range");
        return score;
    }

    boolean eligibleQuietPinThreat(ChessPosition position, Move move) {
        return eligibleQuietQueenPin(position, move)
                || reinforceQueenPin && PawnLeverPolicy.addsAttackOnPinnedQueen(position, move);
    }

    /** One bounded quiet threat: create an absolute pin of the opposing queen. */
    static boolean eligibleQuietQueenPin(ChessPosition position, Move move) {
        if (!quiet(position, move)) return false;
        var kind = position.pieceAt(move.from()).kind();
        if (kind != Kind.ROOK && kind != Kind.BISHOP && kind != Kind.QUEEN) return false;
        // Already pinned queens belong to the separate forced-response frontier.
        if (PawnLeverPolicy.queenPinnedToKing(position, position.turn().other())) return false;
        var next = position.playGenerated(move);
        return !next.isCheck() && PawnLeverPolicy.queenPinnedToKing(next);
    }

    boolean eligibleQuietCheck(ChessPosition position, Move move) {
        var next = position.playGenerated(move);
        if (!next.isCheck()) return false;
        if (!checkingForksOnly || !next.hasLegalMove()) return true;
        for (int square = 0; square < 64; square++) {
            var victim = next.pieceAt(square);
            if (victim != null && victim.side() == next.turn() && (victim.kind() == Kind.QUEEN || victim.kind() == Kind.ROOK)
                    && next.reachesFrom(move.to(), square)) return true;
        }
        return false;
    }

    private boolean drawn(Context context) {
        return drawPolicy == DrawPolicy.CLAIM_AVAILABLE ? context.drawClaim() : context.automaticDraw();
    }

    private static Node exact(int score) { return new Node(score, Bound.EXACT, HardCollections.list()); }
    private static Node prepend(Move move, Node child, int score) {
        var line = new ArrayList<Move>();
        line.add(move);
        line.addAll(child.line());
        return new Node(score, Bound.EXACT, line);
    }
    private static Node window(Node node, int alpha, int beta) {
        return new Node(node.score(), node.score() <= alpha ? Bound.UPPER
                : node.score() >= beta ? Bound.LOWER : Bound.EXACT, node.line());
    }
    private static List<Move> ordered(ChessPosition position, List<Move> moves, Node saved) {
        Move preferred = saved == null || saved.line().isEmpty() ? null : saved.line().get(0);
        return orderedByMove(position, moves, preferred);
    }
    private void resetOrdering() {
        if (!cutoffOrdering) return;
        for (var row : killers) java.util.Arrays.fill(row, null);
        for (var side : quietHistory) for (var row : side) java.util.Arrays.fill(row, 0);
    }
    private static boolean quiet(ChessPosition board, Move move) {
        return move.promotion() == null && board.pieceAt(move.to()) == null
                && !(board.pieceAt(move.from()).kind() == Kind.PAWN && move.from() % 8 != move.to() % 8);
    }
    private void rewardCutoff(ChessPosition position, Move move, int distance, int depth) {
        if (distance < killers.length && !move.equals(killers[distance][0])) {
            killers[distance][1] = killers[distance][0]; killers[distance][0] = move;
        }
        var history = quietHistory[position.turn().ordinal()][move.from()];
        int value = history[move.to()];
        int bonus = Math.min(512, depth * depth * 8);
        history[move.to()] = value + bonus - value * bonus / 16_384;
    }
    private List<Move> orderedForSearch(ChessPosition position, List<Move> moves, Move preferred, int distance) {
        if (!cutoffOrdering) return orderedByMove(position, moves, preferred);
        var result = new ArrayList<>(moves);
        result.sort(Comparator.comparing((Move move) -> !move.equals(preferred))
                .thenComparingInt(move -> quiet(position, move) ? 1 : 0)
                .thenComparing(Comparator.comparingInt((Move move) -> quiet(position, move)
                        ? distance < killers.length && move.equals(killers[distance][0]) ? 32_769
                        : distance < killers.length && move.equals(killers[distance][1]) ? 32_768
                        : quietHistory[position.turn().ordinal()][move.from()][move.to()]
                        : TacticalAnalysis.captureOrder(position, move)).reversed())
                .thenComparing(Move::uci));
        return result;
    }
    private static List<Move> orderedByMove(ChessPosition position, List<Move> moves, Move preferred) {
        var sorted = new ArrayList<>(moves);
        sorted.sort(Comparator.comparing((Move move) -> !move.equals(preferred))
                .thenComparing(Comparator.comparingInt((Move move) -> TacticalAnalysis.captureOrder(position, move)).reversed())
                .thenComparing(Move::uci));
        return sorted;
    }
    private void remember(Key key, Node result) {
        if (capacity == 0) return;
        if (reuseMoveHints) {
            sharedTable.put(key, result);
            while (sharedTable.size() > capacity) sharedTable.remove(sharedTable.keySet().iterator().next());
        } else {
            table.put(key, result);
            while (table.size() > capacity) table.remove(table.keySet().iterator().next());
        }
    }
    private static boolean insufficient(ChessPosition position) {
        int minors = 0;
        int bishopColor = -1;
        boolean onlyBishops = true;
        for (int square = 0; square < 64; square++) {
            var piece = position.pieceAt(square);
            if (piece == null || piece.kind() == Kind.KING) continue;
            if (piece.kind() != Kind.BISHOP && piece.kind() != Kind.KNIGHT) return false;
            minors++;
            if (piece.kind() == Kind.KNIGHT) onlyBishops = false;
            else {
                int color = (square / 8 + square % 8) & 1;
                if (bishopColor >= 0 && bishopColor != color) onlyBishops = false;
                bishopColor = color;
            }
        }
        return minors <= 1 || onlyBishops;
    }
}
