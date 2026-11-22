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
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.Level;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchCapturesTest {
    private static final Pattern PASSED = Pattern.compile("^ok [0-9]+ - (.+)$");

    @TempDir Path output;

    @Test void captureRowsFollowAiMatchHistoryAndBoardOrientation(Checks check) {
        var match = ChessMatch.start(new MatchSetup(Side.WHITE, Level.BASIC, false));
        for (String move : List.of("e2e4", "d7d5", "e4d5", "d8d5")) match = match.play(ChessPosition.Move.fromUci(move));

        var firstCapture = MatchCaptures.at(match, 3, false);
        check.same("The top tray belongs to the Black AI when White is below", Side.BLACK, firstCapture.top().side());
        check.same("The lower tray follows the human White seat", Side.WHITE, firstCapture.bottom().side());
        check.same("The human capture is shown with its material advantage",
                new MatchMaterial.Seat(List.of(new MatchMaterial.Captured(ChessPosition.Kind.PAWN, 1)), 1),
                firstCapture.bottom().material());
        check.same("A future AI capture does not leak into the selected earlier frame",
                new MatchMaterial.Seat(List.of(), 0), firstCapture.top().material());

        var flipped = MatchCaptures.at(match, 3, true);
        check.same("Flipping the board moves the human tray above the board without changing ownership",
                List.of(Side.WHITE, firstCapture.bottom().material()), List.of(flipped.top().side(), flipped.top().material()));
        check.same("Flipping the board moves the AI tray below the board without changing ownership",
                List.of(Side.BLACK, firstCapture.top().material()), List.of(flipped.bottom().side(), flipped.bottom().material()));

        var afterBotCapture = MatchCaptures.at(match, 4, false);
        var pawn = new MatchMaterial.Seat(List.of(new MatchMaterial.Captured(ChessPosition.Kind.PAWN, 1)), 0);
        check.same("The AI capture appears after its reply", pawn, afterBotCapture.top().material());
        check.same("The human's earlier capture remains visible after the AI reply", pawn, afterBotCapture.bottom().material());
        var returned = MatchCaptures.at(ChessMatch.restore(match.checkpoint()), 3, false);
        check.same("Back navigation restores both trays from the exact historical ply", firstCapture, returned);
    }

    @Test void webCaptureAdapterMatchesTheNativeCapturePainter(Checks check) throws IOException, InterruptedException {
        var root = ProjectRoot.locate();
        var log = output.resolve("web-capture-components.tap");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"), "--test", "--test-reporter=tap",
                root.resolve("web/tests/match-captures.test.mjs").toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        check.same("Mocked Web capture components complete without a browser or emulator", true, completed);
        String transcript = Files.readString(log);
        check.same("Every native-referenced Web capture assertion passes", 0,
                completed ? process.exitValue() : -1);
        var passed = transcript.lines().map(PASSED::matcher).filter(java.util.regex.Matcher::matches)
                .map(result -> result.group(1)).toList();
        check.same("Capture shape, color, grouping, labels, clearing and fixed rows are covered", List.of(
                "AI captures stay in their top and bottom trays through reply and history rendering",
                "captured tray displays repeated pieces, side colors and clears stale frame data",
                "every capturable kind uses the shared silhouette and the captured side color",
                "capture rows provide the same accessible contract in Romanian",
                "captured pieces match the APK silhouette, palette, and icon scale",
                "match markup keeps the board between stable top and bottom capture rows"), passed);
    }
}
