package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** Imports original Node assertion verdicts and values; TAP test counts alone cannot qualify a run. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessWebStockfishTransportTest {
    @TempDir Path output;
    static final ObjectMapper JSON = new ObjectMapper();
    static final List<String> SCRIPTS = List.of("stockfish-client.test.mjs", "stockfish-broker.test.mjs", "stockfish-analysis.test.mjs", "stockfish-presentation.test.mjs", "stockfish-journal.test.mjs");

    @Test void nativeNodeAssertionsAreAuditableInExtent(Checks c) throws Exception {
        Path root = ProjectRoot.locate(); String run = UUID.randomUUID().toString();
        var command = new ArrayList<>(List.of("node", "--test", "--test-reporter=tap"));
        var inventory = new LinkedHashSet<String>();
        var pattern = Pattern.compile("(?m)^test\\('([^']+)'[,)]");
        for (String script : SCRIPTS) {
            var path = root.resolve("web/tests/" + script); command.add(path.toString());
            var names = pattern.matcher(Files.readString(path));
            while (names.find()) if (!inventory.add(names.group(1))) throw new IllegalArgumentException("Duplicate native case name");
        }
        c.same("The native transport inventory retains all 41 fixtures", 41, inventory.size());
        var pb = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.resolve("transport.tap").toFile());
        pb.environment().put("ATF_NODE_RUN_ID", run);
        var process = pb.start(); boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); }
        c.same("Native runner completes within its bounded timeout", true, finished);
        var tap = Files.readString(output.resolve("transport.tap"));
        c.same("Original Node process exit", 0, process.exitValue());
        var rows = read(tap, run, inventory);
        c.same("Native assertion evidence is nonempty", true, !rows.isEmpty());
        for (var row : rows) c.observed(row.get("case").asText() + " · " + row.get("name").asText()
                + " · " + row.get("location").asText(), row.get("expected").asText(), row.get("actual").asText(), row.get("passed").booleanValue());
    }

    static List<JsonNode> read(String tap, String run, Set<String> expectedCases) throws Exception {
        var rows = new ArrayList<JsonNode>(); var counts = new HashMap<String, Integer>(); var passed = new LinkedHashSet<String>();
        for (String text : tap.split("\\R")) {
            if (text.matches("^ok [0-9]+ - .+$")) passed.add(text.replaceFirst("^ok [0-9]+ - ", ""));
            int at = text.indexOf("# ATF_ASSERT "); if (at < 0) continue;
            var row = JSON.readTree(text.substring(at + "# ATF_ASSERT ".length()).replaceAll("\\\\([\\\\#])", "$1"));
            String name = row.path("case").asText(); int next = counts.getOrDefault(name, 0) + 1;
            if (row.path("schema").asInt() != 1 || !run.equals(row.path("runId").asText()) || !expectedCases.contains(name)
                    || !row.path("index").isIntegralNumber() || row.path("index").asInt() != next
                    || !row.path("expected").isTextual() || !row.path("actual").isTextual() || !row.path("passed").isBoolean()
                    || !row.path("name").isTextual() || !row.path("location").isTextual() || row.path("location").asText().isBlank())
                throw new IllegalArgumentException("Foreign, missing or duplicate assertion evidence");
            rows.add(row); counts.put(name, next);
        }
        if (!counts.keySet().equals(expectedCases) || !passed.equals(expectedCases))
            throw new IllegalArgumentException("A required native case has no assertion evidence or did not pass");
        return List.copyOf(rows);
    }

    @Test
    @EnabledIfSystemProperty(named = "chess.stockfish.bundle", matches = ".+")
    void compiledPortsRunTheRealShippedEngine(Checks c) throws Exception {
        String run = UUID.randomUUID().toString(); Path log = output.resolve("compiled-stockfish.tap");
        var pb = new ProcessBuilder("node", "--test", "--test-reporter=tap",
                ProjectRoot.locate().resolve("web/tests/stockfish-integration.test.mjs").toString())
                .directory(ProjectRoot.locate().toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        pb.environment().put("ATF_NODE_RUN_ID", run);
        pb.environment().put("J7_STOCKFISH_BUNDLE", System.getProperty("chess.stockfish.bundle"));
        var process = pb.start(); boolean done = process.waitFor(210, TimeUnit.SECONDS);
        if (!done) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); }
        c.same("Real compiled Web integration completes", true, done);
        c.same("Real WASM runner exits successfully", 0, process.exitValue());
        for (var row : read(Files.readString(log), run, Set.of("Compiled Web ports use the real pinned WASM for hints, openings and persisted review")))
            c.observed(row.get("case").asText() + " · " + row.get("name").asText() + " · " + row.get("location").asText(),
                    row.get("expected").asText(), row.get("actual").asText(), row.get("passed").booleanValue());
    }

    @Test void refusesForeignEmptyAndReplacedReceipts(Checks c) throws Exception {
        String row = "# ATF_ASSERT {\"schema\":1,\"runId\":\"run\",\"case\":\"fixture\",\"index\":1,\"name\":\"comparison\",\"location\":\"fixture.test.mjs:4\",\"expected\":\"a\\#b\",\"actual\":\"a\\#b\",\"passed\":true}";
        String good = "ok 1 - fixture\n" + row;
        c.same("TAP hash escaping is decoded once", "a#b", read(good, "run", Set.of("fixture")).get(0).get("actual").asText());
        for (String bad : List.of("ok 1 - fixture", good.replace("\"run\"", "\"other\""), good + "\n" + row,
                good.replace("\"index\":1", "\"index\":2"), good.replace("\"passed\":true", "\"passed\":\"true\"")))
            c.rejects("No zero-assertion or substituted green receipt", IllegalArgumentException.class, () -> read(bad, "run", Set.of("fixture")));
        var falseRow = read(good.replace("\"passed\":true", "\"passed\":false"), "run", Set.of("fixture")).get(0);
        c.same("A native failure is never recomputed from matching display strings", false, falseRow.get("passed").booleanValue());
    }

    @Test void manifestIncludesNestedVendorBytes(Checks c) throws Exception {
        Path runtime = output.resolve("runtime"); Files.createDirectories(runtime.resolve("stockfish"));
        Files.writeString(runtime.resolve("engine.mjs"), "engine\n");
        Path wasm = runtime.resolve("stockfish/engine.wasm"); Files.write(wasm, new byte[]{0, 97, 115, 109});
        String expected = "03b14f5e9e1ccd6ae0b2fe8c476c7e5da45f7ab1e4a4ba16a82a774862c3a2a5";
        c.same("The independently computed manifest includes the relative nested path and its complete bytes", expected,
                ChessWebParityPublication.runtimeDigest(runtime));
        Files.write(wasm, new byte[]{0, 97, 115, 109, 1});
        c.same("A substituted nested binary changes the runtime identity", false, expected.equals(ChessWebParityPublication.runtimeDigest(runtime)));
        Files.write(wasm, new byte[]{0, 97, 115, 109});
        c.same("Restoring the same bytes restores the same identity", expected, ChessWebParityPublication.runtimeDigest(runtime));
    }
}
