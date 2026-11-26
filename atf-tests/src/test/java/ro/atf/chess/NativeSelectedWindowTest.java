package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Selected-root search contracts. A completed pair never certifies all roots or a closed tactical frontier. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class NativeSelectedWindowTest {
    private record Case(String id, ChessPosition position, List<String> roots) { }
    private record Probe(int score, boolean exact, List<ChessPosition.Move> suffix) { }

    private static List<Case> cases() throws Exception {
        com.fasterxml.jackson.databind.JsonNode data;
        try (var stream = NativeSelectedWindowTest.class.getResourceAsStream("/hard-lessons/material-regression-witnesses.yaml")) {
            if (stream == null) throw new IllegalStateException("Missing independent contexts");
            data = LearningGameAudit.mapper().readTree(stream);
        }
        var result = new ArrayList<Case>();
        for (var row : data.path("cases")) {
            if (!List.of("training-056", "training-091", "training-096").contains(row.path("id").asText())) continue;
            var w = NativeFrontierWitnesses.read(row);
            result.add(new Case(w.id(), w.game().position(), List.of(w.selected(), w.rejected())));
        }
        if (result.size() != 3) throw new IllegalArgumentException("Missing selected comparison origin");
        return List.copyOf(result);
    }
    private static Class<?> nested(String name) throws Exception {
        return Class.forName("ro.atf.chess.TacticalAnalysis$" + name);
    }
    private static Object budget(int allowance, boolean pvs) throws Exception {
        var constructor = nested("SearchBudget").getDeclaredConstructor(int.class, int.class, int.class);
        constructor.setAccessible(true);
        var value = constructor.newInstance(allowance, 0, 50_000);
        var flag = value.getClass().getDeclaredField("principalVariationSearch"); flag.setAccessible(true); flag.set(value, pvs);
        return value;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Probe search(Case c, String root, int alpha, int beta, Object budget) throws Exception {
        var move = ChessPosition.Move.fromUci(root);
        Class objective = nested("Objective");
        var method = TacticalAnalysis.class.getDeclaredMethod("search", ChessPosition.class, int.class,
                int.class, int.class, int.class, nested("SearchBudget"), objective);
        method.setAccessible(true);
        Object line;
        try { line = method.invoke(null, c.position().play(move), 4, 1, -beta, -alpha, budget, Enum.valueOf(objective, "POSITION")); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw error;
        }
        var score = line.getClass().getDeclaredMethod("score"); score.setAccessible(true);
        var exact = line.getClass().getDeclaredMethod("exact"); exact.setAccessible(true);
        var moves = line.getClass().getDeclaredMethod("moves"); moves.setAccessible(true);
        return new Probe(-(Integer) score.invoke(line), (Boolean) exact.invoke(line),
                (List<ChessPosition.Move>) moves.invoke(line));
    }
    private static void legal(Checks check, Case c, String root, Probe probe) {
        var end = c.position().play(ChessPosition.Move.fromUci(root));
        for (var move : probe.suffix()) end = end.play(move);
        check.same(c.id() + " ordinary H5 PV is legal and cannot inherit uncharged extension plies", true, probe.suffix().size() <= 4);
    }
    @Test void fullWindowPvsAndAlphaBetaAgreeOnBothActualRoots(Checks check) throws Exception {
        for (var c : cases()) for (String root : c.roots()) {
            var ab = search(c, root, -100001, 100001, budget(300000, false));
            var pvs = search(c, root, -100001, 100001, budget(300000, true));
            check.same(c.id() + " " + root + " full-window PVS score equals unreduced alpha-beta", ab.score(), pvs.score());
            check.same("Both full windows completed exactly at the same H5", true, ab.exact() && pvs.exact());
            legal(check, c, root, ab); legal(check, c, root, pvs);
        }
    }
    @Test void aNarrowWindowCannotPoisonTheSubsequentFullWindow(Checks check) throws Exception {
        for (var c : cases()) for (String root : c.roots()) {
            var cold = search(c, root, -100001, 100001, budget(300000, true));
            for (boolean upper : List.of(false, true)) {
                var warmBudget = budget(300000, true);
                var bound = upper ? search(c, root, cold.score(), cold.score() + 1, warmBudget)
                        : search(c, root, cold.score() - 1, cold.score(), warmBudget);
                check.same("The null-window result stays a bound, not an exact selected-root score", false, bound.exact());
                var full = search(c, root, -100001, 100001, warmBudget);
                check.same(c.id() + " " + root + " warmed/full equals cold/full after " + (upper ? "fail low" : "fail high"), cold.score(), full.score());
                check.same("The completed full-window result is exact", true, full.exact()); legal(check, c, root, full);
            }
        }
    }
    @Test void selectedRootOrderDoesNotChangeCompletedPairScores(Checks check) throws Exception {
        for (var c : cases()) {
            var forward = budget(300000, true); var reverse = budget(300000, true);
            var a = search(c, c.roots().getFirst(), -100001, 100001, forward);
            var b = search(c, c.roots().getLast(), -100001, 100001, forward);
            var rb = search(c, c.roots().getLast(), -100001, 100001, reverse);
            var ra = search(c, c.roots().getFirst(), -100001, 100001, reverse);
            check.same(c.id() + " first root has the same full-window score in either order", a.score(), ra.score());
            check.same(c.id() + " second root has the same full-window score in either order", b.score(), rb.score());
        }
    }
    @Test void anExhaustedSelectedRootHasNoCompletedScore(Checks check) throws Exception {
        for (var c : cases()) {
            var zero = budget(0, true);
            var failure = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> { try { search(c, c.roots().getFirst(), -100001, 100001, zero); }
                    catch (RuntimeException expected) { throw expected; }
                    catch (Exception error) { throw new IllegalStateException(error); } });
            check.same(c.id() + " the failure is actual budget exhaustion, not a broken reflection or fixture",
                    "BudgetExhausted", failure.getClass().getSimpleName());
        }
    }
}
