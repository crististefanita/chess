import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import ro.atf.chess.OpeningCatalog;

/** Run with the compiled Chess core and its resources on the classpath. */
public class ExportOpeningReferenceInputs {
    public static void main(String[] args) {
        var prefixes = new LinkedHashSet<String>();
        for (var opening : OpeningCatalog.load()) {
            for (int ply = 0; ply <= opening.steps().size(); ply++) {
                prefixes.add(opening.steps().stream().limit(ply).map(step -> step.move().uci()).collect(Collectors.joining(" ")));
            }
        }
        for (var prefix : prefixes) System.out.println(prefix);
    }
}
