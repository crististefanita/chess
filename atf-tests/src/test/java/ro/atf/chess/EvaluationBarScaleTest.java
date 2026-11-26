package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.TacticalAnalysis.Ending;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class EvaluationBarScaleTest {
    @TempDir Path compiled;

    @Test void moderateAdvantagesRemainReadableWithoutSaturatingTheTrack(Checks c) {
        int[] scores = {0, 100, 200, 400, 800, 1600};
        double[] shares = {.5, .6, 2.0 / 3, .75, 5.0 / 6, .9};
        for (int i = 0; i < scores.length; i++) {
            c.same(scores[i] + " cp occupies the agreed visual interval", true, Math.abs(share(scores[i]) - shares[i]) < 1e-12);
            c.same(scores[i] + " cp has a color-symmetric opposite", true, Math.abs(share(scores[i]) + share(-scores[i]) - 1) < 1e-12);
        }
        c.same("The label retains the exact Stockfish pawn value", "+8.00", new MatchEvaluation(Ending.HORIZON, 800, 0).pawnLabel(Locale.US));
        c.same("The Romanian label retains its original decimal comma", "−8,00",
                new MatchEvaluation(Ending.HORIZON, -800, 0).pawnLabel(Locale.forLanguageTag("ro-RO")));
    }

    @Test void finiteScoresAreMonotonicAndOnlyMateOrAResultFillsTheTrack(Checks c) {
        var failures = new ArrayList<Integer>();
        for (int cp = -50000; cp <= 50000; cp++) {
            double current = share(cp);
            if (!(current > 0 && current < 1 && current > share(cp - 1))) failures.add(cp);
        }
        c.same("Every permitted external centipawn score stays strictly inside the track and ordered", List.of(), failures);
        c.same("Integer extremes cannot overflow into the wrong color or a full segment", true,
                share(Integer.MIN_VALUE) > 0 && share(Integer.MIN_VALUE) < .5
                        && share(Integer.MAX_VALUE) > .5 && share(Integer.MAX_VALUE) < 1);
        c.same("Known White and Black mate estimates retain their distinct endpoints", List.of(1.0, 0.0),
                List.of(new MatchEvaluation(Ending.CHECKMATE, 0, 3).whiteShare(), new MatchEvaluation(Ending.CHECKMATE, 0, -3).whiteShare()));
        c.same("Stalemate stays neutral even with a stray material value", .5, new MatchEvaluation(Ending.STALEMATE, 900, 0).whiteShare());
        c.same("Final White and Black wins retain the actual result endpoints", List.of(1.0, 0.0),
                List.of(MatchEvaluation.terminalWhiteShare(ChessMatch.Result.WHITE_WINS), MatchEvaluation.terminalWhiteShare(ChessMatch.Result.BLACK_WINS)));
    }

    @Test void webAndAndroidExecuteTheSameScaleWithoutChangingTheEngineObservation(Checks c) throws Exception {
        var root = ProjectRoot.locate();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        int status = ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-proc:none", "-encoding", "UTF-8",
                "-classpath", classpath, "-d", compiled.toString(),
                root.resolve("android/app/src/main/java/ro/atf/chess/android/CoachingPresentation.java").toString(),
                root.resolve("web/src/main/java/ro/atf/chess/WebExternalAnalysis.java").toString(),
                root.resolve("web/src/main/java/ro/atf/chess/WebBoardState.java").toString());
        c.same("Both real platform adapters compile against the shared model", 0, status);
        try (var loader = new URLClassLoader(new java.net.URL[] {compiled.toUri().toURL()}, getClass().getClassLoader())) {
            var android = loader.loadClass("ro.atf.chess.android.CoachingPresentation").getMethod("whiteShare", MatchHint.class, ChessPosition.class);
            var web = loader.loadClass("ro.atf.chess.WebExternalAnalysis").getDeclaredMethod("whiteShare", ChessPosition.class, ExternalEngineAnalysis.Batch.class);
            web.setAccessible(true);
            for (var match : List.of(ChessMatch.start(true), ChessMatch.restore("2|analysis|e2e4"))) {
                int sign = match.position().turn() == ChessPosition.Side.WHITE ? 1 : -1;
                String move = sign == 1 ? "e2e4" : "e7e5";
                for (int cp : new int[] {-50000, -900, -100, 0, 100, 300, 800, 50000}) {
                    var line = new ExternalEngineAnalysis.Line(1, 12, sign * cp, null, List.of(1000, 0, 0), List.of(ChessPosition.Move.fromUci(move)));
                    var batch = ExternalEngineAnalysisTest.batch(match, match.ply(), line);
                    String before = batch.json();
                    var hint = MatchHint.external(match.position(), batch);
                    String label = match.position().turn() + " / " + cp + " cp";
                    c.same(label + " Web projects the shared White score", share(cp), web.invoke(null, match.position(), batch));
                    c.same(label + " Android projects the same White score", share(cp), android.invoke(null, hint, match.position()));
                    c.same(label + " retains raw CP, WDL, PV, depth and provider byte for byte", before, batch.json());
                }
            }
        }
    }

    private static double share(int cp) { return new MatchEvaluation(Ending.HORIZON, cp, 0).whiteShare(); }

    @Test void renderedWebTrackPreservesTheScaleAndOrientationWithAuditedAssertions(Checks c) throws Exception {
        var root = ProjectRoot.locate();
        String run = UUID.randomUUID().toString();
        var log = compiled.resolve("evaluation-bar.tap");
        var builder = new ProcessBuilder("node", "--test", "--test-reporter=tap", root.resolve("web/tests/evaluation-bar-rendering.test.mjs").toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("ATF_NODE_RUN_ID", run);
        var process = builder.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        c.same("The real Web rendering component completes within its timeout", true, finished);
        c.same("The original Node runner exits successfully", 0, process.exitValue());
        var rows = ChessWebStockfishTransportTest.read(Files.readString(log), run, Set.of(
                "Moderate analysis keeps its share through both board orientations",
                "A finite large advantage cannot round the rendered track to a terminal endpoint",
                "Mate endpoints and unavailable analysis retain their separate display states"));
        for (var row : rows) c.observed(row.get("case").asText() + " / " + row.get("name").asText(),
                row.get("expected").asText(), row.get("actual").asText(), row.get("passed").booleanValue());
    }
}
