package ro.atf.chess;

import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Explicit, thread-confined offline experiment. No scope means native evaluation
 * and no experimental cap. This is never opened by the application.
 */
final class HardSearchExperiment implements AutoCloseable {
    enum Bonuses { NATIVE, NONE, QUEEN, PRESSURE, BOTH }
    static final class Exhausted extends RuntimeException {
        Exhausted() { super(null, null, false, false); }
    }
    private static final ThreadLocal<HardSearchExperiment> CURRENT = new ThreadLocal<>();
    private final Thread owner = Thread.currentThread();
    final Bonuses bonuses;
    final ToIntFunction<ChessPosition> delta;
    final long limit;
    final int tableEntries;
    long nodes;
    long evaluations;
    long nativeQueenLeaves;
    long nativePressureLeaves;
    long configuredTables;
    int maximumConfiguredTable;
    TacticalAnalysis.SearchReport completed;
    private boolean closed;

    HardSearchExperiment(Bonuses bonuses, ToIntFunction<ChessPosition> delta, long limit) {
        this(bonuses, delta, limit, -1);
    }

    HardSearchExperiment(Bonuses bonuses, ToIntFunction<ChessPosition> delta, long limit, int tableEntries) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested chess experiments are forbidden");
        if (limit < 1) throw new IllegalArgumentException("Total node cap must be positive");
        if (tableEntries < -1 || tableEntries > 1_000_000) throw new IllegalArgumentException("Invalid comparison table limit");
        this.bonuses = Objects.requireNonNull(bonuses);
        this.delta = Objects.requireNonNull(delta);
        this.limit = limit;
        this.tableEntries = tableEntries;
        CURRENT.set(this);
    }

    static void visit() {
        var active = CURRENT.get();
        if (active == null) return;
        if (active.nodes == active.limit) throw new Exhausted();
        active.nodes++;
    }

    static Object identity() { return CURRENT.get(); }

    static int tableLimit(int nativeLimit) {
        var active = CURRENT.get();
        int selected = active == null || active.tableEntries < 0 ? nativeLimit : active.tableEntries;
        if (active != null) {
            active.configuredTables++;
            active.maximumConfiguredTable = Math.max(active.maximumConfiguredTable, selected);
        }
        return selected;
    }

    static void accepted(TacticalAnalysis.Result result, int depth) {
        var active = CURRENT.get();
        if (active != null) active.completed = new TacticalAnalysis.SearchReport(
                new TacticalAnalysis.Result(HardCollections.copyList(result.ranked())), depth, 0);
    }

    static int specializedBonus(int nativeBonus) {
        var active = CURRENT.get();
        return active == null || active.bonuses == Bonuses.NATIVE ? nativeBonus : 0;
    }

    static int evaluate(ChessPosition position, boolean nativeQueen, boolean nativePressure) {
        return evaluate(position, TacticalAnalysis.positionValue(position, position.turn()), nativeQueen, nativePressure);
    }

    static int evaluate(ChessPosition position, int baseScore, boolean nativeQueen, boolean nativePressure) {
        var active = CURRENT.get();
        boolean queen = nativeQueen;
        boolean pressure = nativePressure;
        if (active != null) {
            active.evaluations++;
            if (nativeQueen) active.nativeQueenLeaves++;
            if (nativePressure) active.nativePressureLeaves++;
            if (active.bonuses != Bonuses.NATIVE) {
                queen = active.bonuses == Bonuses.QUEEN || active.bonuses == Bonuses.BOTH;
                pressure = active.bonuses == Bonuses.PRESSURE || active.bonuses == Bonuses.BOTH;
            }
        }
        int score = baseScore;
        if (queen) score += QueenCounterplay.bonus(position);
        if (pressure) {
            int maximum = HardCandidateSearch.MATE - MatchAiPolicy.MAX_PLIES - 3;
            score = Math.max(-maximum, Math.min(maximum, score + PawnLeverPolicy.kingAttackPressure(position)));
        }
        if (active != null) score += active.delta.applyAsInt(position);
        return score;
    }

    @Override public void close() {
        if (Thread.currentThread() != owner || closed || CURRENT.get() != this) {
            throw new IllegalStateException("Experiment must be closed exactly once by its owner");
        }
        closed = true;
        CURRENT.remove();
    }
}
