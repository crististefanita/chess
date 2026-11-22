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
class AnalysisCaptureParityTest {
    @TempDir Path output;

    @Test void studyCapturesFollowTheDisplayedHistoryOnBothAdapters(Checks check) throws Exception {
        var source = ChessMatch.restore("1|e2e4 d7d5 e4d5 d8d5");
        var study = new AnalysisLine(source, 3);
        var first = MatchCaptures.at(study.line(), study.ply(), false);
        check.same("At ply 3 the lower White study seat has the captured pawn", ChessPosition.Kind.PAWN,
                first.bottom().material().captured().getFirst().kind());
        check.same("The upper Black study seat has no future captures", 0, first.top().material().captured().size());
        check.same("The study frame equals the already governed APK match frame", MatchCaptures.at(source, 3, false), first);

        var reply = MatchCaptures.at(study.seek(4).line(), 4, false);
        check.same("The Black reply adds its captured pawn only at ply 4", ChessPosition.Kind.PAWN,
                reply.top().material().captured().getFirst().kind());
        var flipped = MatchCaptures.at(study.seek(4).line(), 4, true);
        check.same("Flipping the board changes owner rows without changing captures", reply.bottom().material(),
                flipped.top().material());
        var earlier = MatchCaptures.at(study.seek(0).line(), 0, true);
        check.same("Returning to the start clears both capture trays", 0,
                earlier.top().material().captured().size() + earlier.bottom().material().captured().size());
        var restored = MatchCaptures.at(ChessMatch.restore(source.checkpoint()), 0, true);
        check.same("Reload preserves the orientation and exact capture-free cursor", earlier, restored);
    }

    @Test void mockedWebAnalysisRowsRetainTheExistingNavigationContract(Checks check) throws Exception {
        var root = ProjectRoot.locate();
        var log = output.resolve("analysis-web.tap");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"), "--test", "--test-reporter=tap",
                root.resolve("web/tests/analysis-study.test.mjs").toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        check.same("Web analysis components finish without an emulator", true, completed);
        check.same("Existing navigation tests and the new captured-seat test all pass", 0, completed ? process.exitValue() : -1);
        check.same("The selected capture frame and replay clearing are exercised", true,
                Files.readString(log).contains("Analysis uses the AI match capture trays at the selected ply and clears them in replay"));
    }
}
