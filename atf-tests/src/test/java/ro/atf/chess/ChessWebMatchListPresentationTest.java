package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessWebMatchListPresentationTest {
    @TempDir Path output;

    @Test void opponentLabelsAreCompleteBeforeEitherMenuRenders(Checks check) throws Exception {
        var root = ProjectRoot.locate();
        var log = output.resolve("match-list-row.tap");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"), "--test", "--test-reporter=tap",
                root.resolve("web/tests/match-list-row.test.mjs").toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        String transcript = Files.readString(log);
        check.same("Browser-independent list labels finish", true, completed);
        check.same("Every AI level and both languages pass", 0, completed ? process.exitValue() : -1);
        check.same("A malformed AI level cannot silently leave stale rows", true,
                transcript.contains("A malformed saved AI level fails explicitly"));
    }
}
