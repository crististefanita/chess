package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessWebAnalysisLayoutTest {
    @TempDir Path output;
    @Test void analysisKeepsItsBoardAndControlsTogether(Checks c) throws Exception {
        String run = UUID.randomUUID().toString(); Path log = output.resolve("analysis-layout.tap");
        var pb = new ProcessBuilder("node", "--test", "--test-reporter=tap",
                ProjectRoot.locate().resolve("web/tests/analysis-layout.test.mjs").toString())
                .directory(ProjectRoot.locate().toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        pb.environment().put("ATF_NODE_RUN_ID", run); var process = pb.start();
        boolean done = process.waitFor(30, TimeUnit.SECONDS);
        if (!done) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); }
        c.same("Layout components complete without a browser", true, done);
        c.same("Native layout assertion process exit", 0, process.exitValue());
        for (var row : ChessWebStockfishTransportTest.read(Files.readString(log), run, Set.of(
                "The manual board stage has intrinsic height instead of pushing all feedback to the viewport bottom",
                "Feedback reserves the hint row while position and navigation remain adjacent to the board")))
            c.observed(row.get("name").asText() + " · " + row.get("location").asText(),
                    row.get("expected").asText(), row.get("actual").asText(), row.get("passed").booleanValue());
    }
}
