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
class ReviewProgressWebComponentTest {
    @TempDir Path output;
    @Test void mockedWorkerAndStorageGovernIncrementalReviewWithoutBrowser(Checks check) throws Exception {
        var root = ProjectRoot.locate(); var tap = output.resolve("review-progress.tap");
        var process = new ProcessBuilder("node", "--test", "--test-reporter=tap", root.resolve("web/tests/review-progress.test.mjs").toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(tap.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        check.same("The controlled worker and durable-store probes finish", true, finished);
        var receipt = Files.readString(tap);
        check.same("The eight journal, joining, coalescing and failure contracts pass", 0, process.exitValue());
        check.same("All eight component cases actually executed", 8L, receipt.lines().filter(row -> row.matches("ok [0-9]+ - .*" )).count());
        check.same("No case was skipped", true, receipt.contains("# skipped 0"));
    }
}
