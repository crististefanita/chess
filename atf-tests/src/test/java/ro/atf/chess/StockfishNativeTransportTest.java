package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Actual host process integration; it does not qualify an Android executable or replace APK sanity. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
@EnabledIfSystemProperty(named = "chess.stockfish.executable", matches = ".+")
class StockfishNativeTransportTest {
    @Test void realStockfishDrivesHintsAndReviewWithRawUciEvidence(Checks c) throws Exception {
        Path executable = Path.of(System.getProperty("chess.stockfish.executable")).toAbsolutePath();
        Path evidence = Path.of(System.getProperty("chess.stockfish.receipts")).toAbsolutePath();
        Files.createDirectories(evidence);
        String binary = hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(executable)));
        var provider = new ExternalEngineAnalysis.Provider("stockfish-19-host-transport", "19", binary,
                "edb0d9db6731067ec50ce619ff372b463bc4dd5d",
                "{\"threads\":1,\"hashMb\":32,\"nodes\":200000,\"limitStrength\":false}");
        var phases = new ArrayList<StockfishTransport.Search>();
        var processes = new ArrayList<Process>();
        var coaching = new StockfishCoaching(() -> {
            try {
                var builder = new ProcessBuilder(executable.toString()).directory(evidence.toFile()).redirectErrorStream(true);
                builder.environment().put("TMP", evidence.toString()); builder.environment().put("TEMP", evidence.toString());
                var process = builder.start(); processes.add(process);
                return StockfishTransport.process(process, provider, 200000, 3);
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }, (phase, result) -> {
            try {
                phases.add(result); String stem = String.format("phase-%02d", phases.size());
                Files.writeString(evidence.resolve(stem + ".uci"), result.transcript(), StandardCharsets.UTF_8);
                Files.writeString(evidence.resolve(stem + ".json"), "{\"phase\":" + ExternalEngineAnalysis.quote(phase)
                        + ",\"context\":" + result.batch().context().json() + ",\"batch\":" + result.batch().json() + "}", StandardCharsets.UTF_8);
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        });
        try {
            var match = ChessMatch.restore("2|analysis|e2e4 e7e5");
            var hint = coaching.hint(match, 1);
            c.same("A real process supplies the selected Black position", provider, hint.external().orElseThrow().provider());
            c.same("All returned candidate moves are legal", true, hint.external().orElseThrow().lines().stream()
                    .allMatch(line -> match.positionAt(1).legalMoves().contains(line.root())));
            c.same("The engine returns an actual observed completed depth", true, hint.external().orElseThrow().depth() > 0);
            var review = coaching.review(match, false, ignored -> { });
            c.same("Real free/played observations cover every ply", 2, review.external().turns().size());
            for (var turn : review.external().turns()) c.same("Same completed depth at ply " + turn.ply(), turn.free().depth(), turn.played().depth());
            for (int index = 0; index < phases.size(); index++) {
                var phase = phases.get(index);
                c.same("Raw UCI position retained for phase " + (index + 1), true, phase.transcript().contains("> position startpos"));
                c.same("Raw bestmove retained for phase " + (index + 1), true, phase.transcript().contains("< bestmove " + phase.bestmove()));
                c.same("Reported nodes are measured, not the requested value", true, phase.batch().best().nodes() != null && phase.batch().best().nodes() > 0);
                c.observed("Engine evidence phase " + (index + 1), "Validated Stockfish batch and exact context", phase.batch().json(), true);
            }
        } finally {
            coaching.close();
            for (Process process : processes) {
                boolean exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) { process.destroyForcibly(); exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS); }
                Files.writeString(evidence.resolve("process-" + processes.indexOf(process) + "-exit.json"),
                        "{\"exited\":" + exited + ",\"exitCode\":" + (exited ? process.exitValue() : "null") + ",\"buildSha256\":\"" + binary + "\"}");
                c.same("Actual engine process is closed", true, exited);
            }
        }
    }
    private static String hex(byte[] bytes) { var out = new StringBuilder(); for (byte b : bytes) out.append(String.format("%02x", b & 255)); return out.toString(); }
}
