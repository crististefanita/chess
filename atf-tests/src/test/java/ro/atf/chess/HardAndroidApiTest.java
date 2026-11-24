package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class HardAndroidApiTest {
    private static final List<String> CLASSES = List.of("HardCandidateSearch", "HardMaster", "HardMasterPolicy",
            "HardMateCertificate", "HardSearchExperiment", "HardCandidateEvaluation", "PawnLeverPolicy", "QueenCounterplay", "HardCollections");

    @Test void theNewExperimentalSourcesAvoidPostApi26CollectionCalls(Checks check) throws Exception {
        var failures = new ArrayList<String>();
        for (String name : CLASSES) {
            var file = ProjectRoot.locate().resolve("chess-core/src/main/java/ro/atf/chess/" + name + ".java");
            for (String issue : unsupported(Files.readString(file))) failures.add(name + ":" + issue);
        }
        check.same("All nine experimental runtime classes use API26-compatible collection calls", List.of(), failures);
    }

    @Test void theScannerRejectsModernFactoriesAndSequencedCallsButIgnoresExamples(Checks check) throws Exception {
        for (String expression : List.of("moves.getFirst()", "moves.getLast()", "moves.reversed()", "stream.toList()",
                "List.of(1)", "java.util.List.copyOf(moves)", "Set.of(1)", "Map.of()")) {
            check.same("Reject unsupported runtime call " + expression, 1,
                    unsupported("class Probe { Object f() { return " + expression + "; } }").size());
        }
        check.same("Ordinary indexing and the supported collector remain available", List.of(), unsupported(
                "class Probe { Object f() { return stream.collect(java.util.stream.Collectors.toList()); } Object g() { return moves.get(0); } }"));
        check.same("API24 Comparator reversal is distinct from the new collection reversal", List.of(), unsupported(
                "class Probe { Object f() { return java.util.Comparator.comparingInt(value -> 1).reversed(); } Object g() { Comparator<String> order = Comparator.naturalOrder(); return order.reversed(); } }"));
        check.same("Quoted examples and comments do not count as API invocations", List.of(), unsupported(
                "class Probe { String text = \"moves.getFirst() List.of(1)\"; /* stream.toList(); */ }"));
        check.same("Sequenced collection types are rejected independently", 1, unsupported("class Probe { SequencedCollection values; }").size());
        check.same("Qualified sequenced types cannot bypass the guard", 1, unsupported("class Probe { java.util.SequencedCollection values; }").size());
    }

    @Test void compatibleFactoriesKeepDefensiveCopiesNullRejectionAndImmutability(Checks check) {
        var input = new ArrayList<>(Arrays.asList("a", "b"));
        var copy = HardCollections.copyList(input); input.set(0, "changed");
        check.same("A published immutable list cannot change through its source", Arrays.asList("a", "b"), copy);
        check.rejects("Factory lists are immutable", UnsupportedOperationException.class, () -> HardCollections.list("a").add("b"));
        check.rejects("Copied lists are immutable", UnsupportedOperationException.class, () -> copy.set(0, "c"));
        check.rejects("Null list elements remain rejected", NullPointerException.class, () -> HardCollections.list("a", null));
        check.rejects("Null copied elements remain rejected", NullPointerException.class, () -> HardCollections.copyList(Arrays.asList("a", null)));
        check.rejects("Factory sets are immutable", UnsupportedOperationException.class, () -> HardCollections.set("a").remove("a"));
        check.rejects("Duplicate set elements remain rejected", IllegalArgumentException.class, () -> HardCollections.set("a", "a"));
        check.rejects("Null set elements remain rejected", NullPointerException.class, () -> HardCollections.set("a", null));
        check.same("Empty factory lists remain empty", 0, HardCollections.list().size());
    }

    private static List<String> unsupported(String source) throws Exception {
        var file = new SimpleJavaFileObject(URI.create("string:///Probe.java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignored) { return source; }
        };
        var found = new ArrayList<String>();
        var task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, null, diagnostic -> { }, List.of("-proc:none"), null, List.of(file));
        var sequenced = Set.of("SequencedCollection", "SequencedSet", "SequencedMap");
        var modern = Set.of("getFirst", "getLast", "addFirst", "addLast", "removeFirst", "removeLast", "reversed");
        var factories = Set.of("List", "Set", "Map", "java.util.List", "java.util.Set", "java.util.Map");
        var comparators = new java.util.HashSet<String>();
        var scan = new TreeScanner<Void, Void>() {
            @Override public Void visitMethodInvocation(MethodInvocationTree call, Void ignored) {
                if (call.getMethodSelect() instanceof MemberSelectTree select) {
                    String method = select.getIdentifier().toString(), owner = select.getExpression().toString();
                    boolean comparator = owner.startsWith("Comparator.") || owner.startsWith("java.util.Comparator.") || comparators.contains(owner);
                    if (modern.contains(method) && (!method.equals("reversed") || !comparator) || method.equals("toList") && !owner.equals("Collectors") && !owner.equals("java.util.stream.Collectors")
                            || factories.contains(owner) && Set.of("of", "ofEntries", "copyOf", "entry").contains(method)) found.add(select.toString());
                }
                return super.visitMethodInvocation(call, ignored);
            }
            @Override public Void visitVariable(VariableTree variable, Void ignored) {
                String type = variable.getType() == null ? "" : variable.getType().toString();
                if (type.equals("Comparator") || type.startsWith("Comparator<") || type.equals("java.util.Comparator") || type.startsWith("java.util.Comparator<")) {
                    comparators.add(variable.getName().toString());
                }
                return super.visitVariable(variable, ignored);
            }
            @Override public Void visitMemberSelect(MemberSelectTree name, Void ignored) {
                if (sequenced.contains(name.getIdentifier().toString())) found.add(name.toString());
                return super.visitMemberSelect(name, ignored);
            }
            @Override public Void visitIdentifier(IdentifierTree name, Void ignored) {
                if (sequenced.contains(name.getName().toString())) found.add(name.toString());
                return super.visitIdentifier(name, ignored);
            }
        };
        for (var unit : task.parse()) scan.scan(unit, null);
        return List.copyOf(found);
    }
}