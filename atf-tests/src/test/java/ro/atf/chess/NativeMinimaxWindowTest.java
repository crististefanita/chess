package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Independent exhaustive tree enumeration; the leaf evaluator is deliberately shared, not a teacher. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class NativeMinimaxWindowTest {
    private record Value(int score, boolean exact) { }
    private static final List<String> POSITIONS = List.of(
            "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1",
            "6k1/8/8/8/8/3q4/3R4/6K1 w - - 0 1",
            "7k/P7/8/8/8/8/6p1/6K1 w - - 0 1",
            "k7/8/8/8/4N3/8/5p1K/8 b - - 0 1");

    private static Class<?> nested(String name) throws Exception {
        return Class.forName("ro.atf.chess.TacticalAnalysis$" + name);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object objective() throws Exception {
        return Enum.valueOf((Class) nested("Objective"), "POSITION");
    }
    private static Value value(Object line) throws Exception {
        var score = line.getClass().getDeclaredMethod("score"); score.setAccessible(true);
        var exact = line.getClass().getDeclaredMethod("exact"); exact.setAccessible(true);
        return new Value((Integer) score.invoke(line), (Boolean) exact.invoke(line));
    }
    private static int minimax(ChessPosition p, int remaining, int distance) throws Exception {
        var moves = p.legalMoves();
        if (remaining == 0 || moves.isEmpty()) {
            var leaf = TacticalAnalysis.class.getDeclaredMethod("leaf", ChessPosition.class, int.class, boolean.class, nested("Objective"));
            leaf.setAccessible(true);
            return value(leaf.invoke(null, p, distance, !moves.isEmpty(), objective())).score();
        }
        int best = Integer.MIN_VALUE;
        for (var move : moves) best = Math.max(best, -minimax(p.play(move), remaining - 1, distance + 1));
        return best;
    }
    private static Object budget(boolean pvs) throws Exception {
        var constructor = nested("SearchBudget").getDeclaredConstructor(int.class, int.class, int.class);
        constructor.setAccessible(true);var result = constructor.newInstance(100_000, 0, 4_096);
        var flag = result.getClass().getDeclaredField("principalVariationSearch"); flag.setAccessible(true); flag.set(result, pvs);
        return result;
    }
    private static Value search(ChessPosition p, int horizon, int alpha, int beta, Object budget) throws Exception {
        var method = TacticalAnalysis.class.getDeclaredMethod("search", ChessPosition.class, int.class, int.class,
                int.class, int.class, nested("SearchBudget"), nested("Objective"));
        method.setAccessible(true);
        try { return value(method.invoke(null, p, horizon, 0, alpha, beta, budget, objective())); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw error;
        }
    }
    @Test void completePvsAndAlphaBetaEqualExhaustiveMinimax(Checks check) throws Exception {
        for (String fen : POSITIONS) for (int horizon = 1; horizon <= 3; horizon++) {
            var p = ChessPosition.fromFen(fen); int expected = minimax(p, horizon, 0);
            for (boolean pvs : List.of(false, true)) {
                var observed = search(p, horizon, -100001, 100001, budget(pvs));
                check.same("Exhaustive shared-leaf minimax at H" + horizon + ", PVS=" + pvs + ", " + fen, expected, observed.score());
                check.same("Completed full-window search is exact", true, observed.exact());
            }
        }
    }
    @Test void nullWindowsProveDirectionWithoutInventingExactScores(Checks check) throws Exception {
        for (String fen : POSITIONS) {
            var p = ChessPosition.fromFen(fen); int expected = minimax(p, 3, 0);
            var lower = search(p, 3, expected - 1, expected, budget(true));
            var upper = search(p, 3, expected, expected + 1, budget(true));
            check.same("At the known truth boundary, fail-high reaches the exhaustive score", expected, lower.score());
            check.same("At the known truth boundary, fail-low reaches the exhaustive score", expected, upper.score());
            check.same("Neither boundary value is relabelled exact", false, lower.exact() || upper.exact());
        }
    }
    @Test void strictParentComparisonExcludesATieAndSurvivesWarmCache(Checks check) throws Exception {
        for (String fen : POSITIONS) {
            var p = ChessPosition.fromFen(fen); int exact = minimax(p, 3, 0);
            var shared = budget(true);
            // Parent=exact+1: a fail-low at [parent-1,parent] proves the challenger strictly worse.
            var below = search(p, 3, exact, exact + 1, shared);
            check.same("Sufficient upper bound proves a strict parent win", true, below.score() <= exact);
            // Parent=exact: the same question must not turn equality into a veto.
            var tied = search(p, 3, exact - 1, exact, shared);
            check.same("A tie fails high and supplies no strictly-worse upper bound", true, tied.score() >= exact);
            var complete = search(p, 3, -100001, 100001, shared);
            check.same("A warmed complete search still equals exhaustive minimax", exact, complete.score());
            check.same("Only the completed full window is exact", true, complete.exact());
        }
    }
}
