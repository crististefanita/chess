package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchSeatParityTest {
    @TempDir Path output;

    @Test void opponentNamesComeFromOneCoreContract(Checks check) {
        check.same("Basic names the AI player Alex", "Alex", MatchOpponentName.forLevel(MatchSetup.Level.BASIC));
        check.same("Advanced names the AI player Mira", "Mira", MatchOpponentName.forLevel(MatchSetup.Level.ADVANCED));
        check.same("Hard names the AI player Atlas", "Atlas", MatchOpponentName.forLevel(MatchSetup.Level.HARD));
    }

    @Test void browserSeatsAndFixedBoardRowsMatchTheNativeContract(Checks check) throws IOException, InterruptedException {
        var root = ProjectRoot.locate();
        var log = output.resolve("match-seat-components.tap");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"), "--test", "--test-reporter=tap",
                root.resolve("web/tests/match-seats.test.mjs").toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        var transcript = Files.readString(log);
        check.same("The mocked Web seat adapter finishes without a browser or emulator", true, completed);
        check.same("All Web seat and geometry assertions pass", 0, completed ? process.exitValue() : -1);
        for (String name : List.of(
                "AI name and level are adjacent to their captures across orientation and history",
                "all three AI identities match native names without a browser or emulator",
                "localized seat descriptions and long names retain a fixed board row")) {
            check.same("Web component receipt: " + name, true, transcript.contains(" - " + name));
        }
    }
}
