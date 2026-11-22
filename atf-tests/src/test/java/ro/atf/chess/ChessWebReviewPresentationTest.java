package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessWebReviewPresentationTest {
    private static final Pattern PASS = Pattern.compile("^ok [0-9]+ - (.+)$");

    @TempDir Path output;

    @Test void gradeColorsAndFinishedReviewActionAreComponentQualified(Checks check) throws IOException, InterruptedException {
        var log = output.resolve("review-presentation.tap");
        var root = ProjectRoot.locate();
        String node = System.getProperty("chess.web.node", "node");
        var execution = new ProcessBuilder(node, "--test", "--test-reporter=tap",
                root.resolve("web/tests/review-presentation.test.mjs").toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed = execution.waitFor(30, TimeUnit.SECONDS);
        if (!completed) execution.destroyForcibly();
        check.same("The offline Web presentation component completes", 0, completed ? execution.exitValue() : -1);
        try (var lines = Files.lines(log)) {
            check.same("Every presentation contract passes", List.of(
                            "Every review grade keeps text and receives the APK color group",
                            "Finished review action is terminal-only and uses the shared accessible route"),
                    lines.map(PASS::matcher).filter(matcher -> matcher.matches()).map(matcher -> matcher.group(1)).toList());
        }
    }
}
