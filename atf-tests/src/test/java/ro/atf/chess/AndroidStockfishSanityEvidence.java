package ro.atf.chess;

import atf.reporting.contract.AtfAssertion;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Strict, offline import of one closed APK instrumentation run. Never runs a chess engine. */
final class AndroidStockfishSanityEvidence {
    static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static final String PHASE = "stockfish-only";
    static final String SOURCE = "0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6";
    static final String NETWORK = "61e7af4bb97d51eeeb25d322916f86513b5cd3a827ce189c98c6e31946f99e5b";
    static final List<String> NAMES = names();
    record NativeReceipt(List<AtfAssertion> rows, Map<String, String> markers) { }
    record Observation(String name, ChessMatch match, int ply, ExternalEngineAnalysis.Batch batch) { }
    record Evidence(Path root, JsonNode execution, JsonNode smoke, List<AtfAssertion> rows,
                    List<AtfAssertion> integrity, byte[] screenshot, Map<String, Path> inputs, int journalCount) { }

    private AndroidStockfishSanityEvidence() { }

    static Evidence verify(Path input) throws Exception {
        Path root = input.toRealPath(); Path manifestPath = root.resolve("native-exit.json");
        var execution = read(manifestPath, 1_048_576);
        require(execution.path("schema").asInt() == 1 && execution.path("exitCode").isIntegralNumber()
                && execution.path("exitCode").intValue() == 0, "The original native orchestrator is not closed with exit 0");
        String run = text(execution, "runId"); require(run.matches("[a-f0-9]{32}"), "Invalid native run ID");
        require(PHASE.equals(text(execution, "phase")), "Foreign native phase");
        require(text(execution, "sourceCommit").matches("[a-f0-9]{40}"), "Missing APK source commit");
        var inputs = new LinkedHashMap<String, Path>(); inputs.put("native-exit", manifestPath);
        for (String key : List.of("instrumentation", "smoke", "screenshot")) {
            var entry = execution.path("files").path(key);
            Path path = root.resolve(text(entry, "path")).normalize().toRealPath();
            require(path.startsWith(root), "Evidence escapes its captured run folder");
            require(sha(path).equals(text(entry, "sha256")), "Substituted " + key + " bytes");
            inputs.put(key, path);
        }
        var nativeReceipt = readNative(Files.readString(inputs.get("instrumentation")), run);
        var smoke = read(inputs.get("smoke"), 64 * 1_048_576);
        require(smoke.path("schema").asInt() == 1 && run.equals(text(smoke, "runId"))
                && PHASE.equals(text(smoke, "phase"))
                && "ACTUAL_PACKAGED_STOCKFISH_AND_MANUAL_UI".equals(text(smoke, "scope")), "Foreign smoke JSON");
        require(inputs.get("smoke").getFileName().toString().equals("j7-stockfish-" + run + ".json"), "Foreign smoke filename");
        require(text(nativeReceipt.markers(), "stockfishEvidence").equals(inputs.get("smoke").getFileName().toString())
                && text(nativeReceipt.markers(), "stockfishEvidenceSha256").equals(sha(inputs.get("smoke"))), "Smoke JSON differs from the native result marker");
        require("37".equals(text(nativeReceipt.markers(), "stockfishAssertions")), "Wrong native assertion count marker");
        for (int index = 0; index < NAMES.size(); index++) {
            String key = String.format(java.util.Locale.ROOT, "check_%02d", index + 1);
            require(smoke.path("nativeAssertions").path(key).isTextual()
                    && smoke.path("nativeAssertions").path(key).textValue().equals(nativeReceipt.markers().get(key)), "Native row substituted inside smoke JSON: " + key);
        }
        require(smoke.path("nativeAssertions").size() == 38, "Smoke assertion inventory is not 37 rows plus one capture");
        byte[] png = Files.readAllBytes(inputs.get("screenshot"));
        validatePng(png, text(nativeReceipt.markers(), "capture_stockfish-analysis"));
        require(text(smoke.path("nativeAssertions"), "capture_stockfish-analysis").equals(sha(png)), "Smoke capture marker differs from the actual PNG");
        Path requestedApk = Path.of(text(execution, "apkPath"));
        Path apk = (requestedApk.isAbsolute() ? requestedApk : root.resolve(requestedApk)).toRealPath(); inputs.put("apk", apk);
        require(sha(apk).equals(text(execution, "apkSha256")), "The APK bytes do not match the native run binder");
        var observations = observations(smoke);
        var provider = observations.get(0).batch().provider();
        for (var observed : observations) require(provider.equals(observed.batch().provider()), "Mixed engine providers or options in one native smoke");
        verifyPackagedProvider(apk, provider);

        var journals = smoke.path("journals"); require(journals.isArray() && !journals.isEmpty(), "Missing raw UCI journal snapshots");
        long started = smoke.path("startedAtMillis").asLong(-1); require(started > 0, "Missing native start time");
        var receiptIds = new HashSet<String>(); var validated = new ArrayList<ExternalEngineAnalysis.Batch>();
        for (var wrapper : journals) {
            require(text(wrapper, "file").matches("[a-f0-9]{64}-[a-f0-9-]{36}\\.json")
                    && text(wrapper, "sha256").matches("[a-f0-9]{64}") && wrapper.path("bytes").asLong() > 0, "Invalid original journal identity");
            var row = wrapper.path("original");
            require(row.path("schema").asInt() == 1 && "ACTUAL_ANDROID_STOCKFISH_UCI_SEARCH".equals(text(row, "scope"))
                    && NETWORK.equals(text(row, "networkSha256")) && row.path("createdAtMillis").asLong(-1) >= started,
                    "Foreign or stale engine journal");
            require(receiptIds.add(text(row, "receiptID")), "Duplicate actual engine receipt ID");
            require(text(row, "receiptID").matches("[a-f0-9-]{36}")
                    && text(wrapper, "file").endsWith("-" + text(row, "receiptID") + ".json"), "Journal filename belongs to another receipt ID");
            var matching = observations.stream().filter(o -> o.batch().context().contextKey().equals(row.path("context").path("contextKey").asText())).findFirst().orElse(null);
            require(matching != null, "An unrelated journal cannot qualify this smoke");
            var context = ExternalEngineAnalysis.context(matching.match(), matching.ply());
            require(JSON.readTree(context.json()).equals(row.path("context"))
                    && sha(context.json().getBytes(StandardCharsets.UTF_8)).equals(text(row, "contextSha256")), "Native journal context or history hash differs");
            String transcript = text(row, "rawUCITranscript");
            require(sha(transcript.getBytes(StandardCharsets.UTF_8)).equals(text(row, "transcriptSha256")), "Substituted raw UCI transcript");
            String phase = text(row, "phase").replaceFirst("^reuse:", "");
            String forced = phase.equals("free") ? null : phase.startsWith("played:") ? phase.substring(7) : "INVALID";
            require(!"INVALID".equals(forced), "Unknown UCI audit phase");
            verifyCommands(transcript, context, forced, text(row, "bestmove"));
            var candidates = new ArrayList<JsonNode>(); candidates.add(row.path("batch"));
            require(row.path("completedDepthBatches").isArray(), "Missing completed-depth inventory");
            row.path("completedDepthBatches").forEach(candidates::add);
            for (var candidate : candidates) {
                var batch = ExternalEngineAnalysis.batch(matching.match(), matching.ply(), candidate.toString());
                require(batch.provider().equals(provider), "Foreign provider in a retained completed iteration");
                if (candidate == row.path("batch")) require(batch.best().root().uci().equals(text(row, "bestmove")), "Selected batch differs from actual raw bestmove");
                for (var line : batch.lines()) require(rawContains(transcript, line), "The raw UCI output does not contain a retained exact score/WDL/PV");
                validated.add(batch);
            }
        }
        for (var observation : observations) require(validated.contains(observation.batch()), "No raw exact UCI evidence for " + observation.name());
        var integrity = List.of(
                fact("Original orchestrator exit", "0", Integer.toString(execution.path("exitCode").intValue())),
                fact("One native run, JSON and screenshot", run, run),
                fact("Original assertion inventory", "37 ordered PASS rows", nativeReceipt.rows().size() + " ordered PASS rows"),
                fact("Qualified APK bytes", text(execution, "apkSha256"), sha(apk)),
                fact("Actual packaged engine binary", provider.buildSha256(), provider.buildSha256()),
                fact("Complete context and legal observed roots", "3 hints + 4 review observations", "3 hints + 4 review observations"),
                fact("Raw exact output joins", "Every observed score/WDL/PV", observations.size() + " observations with raw exact output"),
                fact("Capture bytes and native marker", text(nativeReceipt.markers(), "capture_stockfish-analysis"), sha(png)));
        return new Evidence(root, execution, smoke, nativeReceipt.rows(), integrity, png, Map.copyOf(inputs), journals.size());
    }

    static NativeReceipt readNative(String content, String run) {
        require(run.matches("[a-f0-9]{32}"), "Invalid expected run ID");
        var markers = new LinkedHashMap<String, String>(); int terminal = 0;
        for (String raw : content.split("\\R")) {
            if (raw.startsWith("INSTRUMENTATION_RESULT: ")) {
                String value = raw.substring("INSTRUMENTATION_RESULT: ".length()); int split = value.indexOf('=');
                require(split > 0 && markers.putIfAbsent(value.substring(0, split), value.substring(split + 1)) == null, "Duplicate or invalid native result marker");
            } else if (raw.startsWith("INSTRUMENTATION_CODE:")) {
                require("-1".equals(raw.substring("INSTRUMENTATION_CODE:".length()).trim()), "Failed native instrumentation code"); terminal++;
            }
        }
        require(terminal == 1 && "PASS".equals(markers.get("result")) && run.equals(markers.get("runId"))
                && PHASE.equals(markers.get("phase")) && markers.getOrDefault("pid", "").matches("[1-9][0-9]*"), "Missing or foreign closed native result");
        var rows = new ArrayList<AtfAssertion>();
        require(markers.keySet().stream().filter(key -> key.startsWith("check_")).count() == NAMES.size(), "Missing or extra native assertions");
        for (int index = 0; index < NAMES.size(); index++) {
            String name = String.format(java.util.Locale.ROOT, "check_%02d", index + 1);
            String[] cells = markers.getOrDefault(name, "").split("\\t", -1);
            require(cells.length == 4 && NAMES.get(index).equals(cells[0]) && "true".equals(cells[3]), "Missing, substituted or failing native assertion " + name);
            rows.add(new AtfAssertion(cells[0], cells[1], cells[2], true));
        }
        return new NativeReceipt(List.copyOf(rows), Map.copyOf(markers));
    }

    static void validatePng(byte[] bytes, String expectedSha) throws IOException {
        require(bytes.length >= 24 && bytes.length <= 16 * 1_048_576 && sha(bytes).equals(expectedSha), "Substituted or absent native PNG");
        require(java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10}), "Evidence is not a PNG");
        var image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        require(image != null && image.getWidth() > 0 && image.getHeight() > 0, "PNG cannot be decoded");
    }

    private static List<Observation> observations(JsonNode smoke) throws Exception {
        require(smoke.path("observations").isArray() && smoke.path("observations").size() == 7, "Missing direct hints or review observations");
        var expected = Set.of("match", "manual", "opening", "review-free-1", "review-played-1", "review-free-2", "review-played-2");
        var seen = new HashSet<String>(); var result = new ArrayList<Observation>();
        for (var node : smoke.path("observations")) {
            String name = text(node, "name"); require(expected.contains(name) && seen.add(name), "Duplicate or foreign coaching route");
            var match = ChessMatch.restore(text(node, "checkpoint")); int ply = node.path("ply").asInt(-1);
            var batch = ExternalEngineAnalysis.batch(match, ply, node.path("batch").toString());
            result.add(new Observation(name, match, ply, batch));
        }
        var reviewed = ChessMatch.restore(text(smoke, "reviewCheckpoint"));
        var review = ExternalEngineAnalysis.review(reviewed, smoke.path("review").toString());
        require(review.turns().size() == 2 && !review.rated() && reviewed.result() != ChessMatch.Result.IN_PROGRESS, "Wrong two-ply unrated review source");
        for (var turn : review.turns()) for (boolean played : List.of(false, true)) {
            String name = "review-" + (played ? "played-" : "free-") + turn.ply();
            var original = result.stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow();
            require(original.match().checkpoint().equals(reviewed.checkpoint()) && original.ply() == turn.ply() - 1
                    && original.batch().equals(played ? turn.played() : turn.free()), "Review branch substituted in observation inventory");
        }
        return result;
    }

    private static void verifyPackagedProvider(Path apk, ExternalEngineAnalysis.Provider provider) throws Exception {
        require("19.0.1".equals(provider.version()) && SOURCE.equals(provider.sourceCommit())
                && provider.providerID().matches("stockfish-19\\.0\\.1-android-lite-(?:arm64-v8a|armeabi-v7a|x86_64)")
                && "{\"threads\":1,\"hashMb\":32,\"nodes\":200000,\"limitStrength\":false}".equals(provider.optionsIdentity()), "Unexpected Android provider policy");
        try (var archive = new ZipFile(apk.toFile())) {
            var entry = archive.getEntry("assets/stockfish/manifest.json"); require(entry != null, "APK has no Stockfish manifest");
            JsonNode manifest; try (var in = archive.getInputStream(entry)) { manifest = JSON.readTree(in); }
            require(manifest.path("schema").asInt() == 1 && SOURCE.equals(text(manifest, "sourceCommit"))
                    && "19.0.1".equals(text(manifest, "version")) && "lite".equals(text(manifest, "flavor")) && manifest.path("minSdk").asInt() == 26
                    && manifest.path("elfPageAlignment").asInt() == 16384, "Foreign APK Stockfish package");
            require(NETWORK.equals(text(manifest.path("network"), "sha256"))
                    && "nn-61e7af4bb97d.nnue".equals(text(manifest.path("network"), "path"))
                    && manifest.path("network").path("bytes").asLong() == 1_166_381, "Foreign Stockfish Lite network");
            var binaries = new ArrayList<JsonNode>(); for (var binary : manifest.path("binaries"))
                if (provider.providerID().equals(binary.path("providerID").asText())) binaries.add(binary);
            require(binaries.size() == 1, "Provider does not identify one packaged ABI"); var binary = binaries.get(0);
            require(text(binary, "abi").matches("arm64-v8a|armeabi-v7a|x86_64"), "Unknown Android ABI");
            var library = archive.getEntry("lib/" + text(binary, "abi") + "/libstockfish.so");
            require(library != null && library.getSize() == binary.path("bytes").asLong(), "Missing packaged engine library");
            try (var in = archive.getInputStream(library)) {
                String hash = sha(in); require(provider.buildSha256().equals(hash) && hash.equals(text(binary, "sha256")), "The observed engine is not the APK binary");
            }
        }
    }

    static void verifyCommands(String transcript, ExternalEngineAnalysis.Context context, String forced, String bestmove) {
        var positions = transcript.lines().filter(line -> line.startsWith("> position ")).toList();
        var go = transcript.lines().filter(line -> line.startsWith("> go ")).toList();
        String position = "> position startpos" + (context.moves().isEmpty() ? "" : " moves " + String.join(" ", context.moves()));
        require(positions.equals(List.of(position)), "Raw UCI position does not match the full historical prefix");
        require(go.equals(List.of("> go nodes 200000" + (forced == null ? "" : " searchmoves " + forced))), "Raw UCI budget or forced root differs");
        var endings = transcript.lines().filter(line -> line.startsWith("< bestmove ")).toList();
        require(endings.size() == 1 && endings.get(0).split("\\s+")[2].equals(bestmove), "Missing or substituted raw bestmove");
    }
    static boolean rawContains(String transcript, ExternalEngineAnalysis.Line expected) {
        return transcript.lines().filter(row -> row.startsWith("< info ")).map(row -> StockfishTransport.info(row.substring(2))).anyMatch(expected::equals);
    }
    static String sha(Path path) throws Exception { try (var stream = Files.newInputStream(path)) { return sha(stream); } }
    private static String sha(java.io.InputStream stream) throws Exception { var digest = MessageDigest.getInstance("SHA-256"); byte[] bytes = new byte[65536]; for (int size; (size = stream.read(bytes)) >= 0;) digest.update(bytes, 0, size); return HexFormat.of().formatHex(digest.digest()); }
    static String sha(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private static JsonNode read(Path path, long maximum) throws IOException { require(Files.isRegularFile(path) && Files.size(path) > 0 && Files.size(path) <= maximum, "Missing or oversized evidence: " + path.getFileName()); return JSON.readTree(Files.readAllBytes(path)); }
    private static String text(JsonNode node, String key) { require(node.path(key).isTextual() && !node.path(key).textValue().isBlank(), "Missing text field: " + key); return node.path(key).textValue(); }
    private static String text(Map<String, String> node, String key) { String value = node.get(key); require(value != null && !value.isBlank(), "Missing native result marker: " + key); return value; }
    private static AtfAssertion fact(String name, String expected, String actual) { return new AtfAssertion(name, expected, actual, true); }
    private static void require(boolean valid, String reason) { if (!valid) throw new IllegalArgumentException(reason); }

    private static List<String> names() {
        var names = new ArrayList<String>(List.of("The signed Stockfish probe has a fresh explicit run identity", "The probe uses the production APK, without an application ID suffix"));
        for (String route : List.of("match", "manual", "opening")) for (String suffix : List.of(
                " uses the pinned packaged Android engine", " preserves the complete history and clocks", " has a positive completed depth",
                " has only legal offered roots", " applies the same recommendation by destination tap", " replays the observed PV without a native leaf estimate")) names.add(route + suffix);
        names.addAll(List.of("The full two-ply review has original external observations", "Both played moves are present",
                "Each review turn compares its actual played root at one common completed depth", "The explicitly unrated review never invents a player rating",
                "The archived external evidence survives its actual codec round trip", "Fresh actual UCI journals are retained, with original transcript hashes"));
        for (String route : List.of("match", "manual", "opening", "review-free-1", "review-played-1", "review-free-2", "review-played-2")) names.add("Original completed UCI evidence binds " + route);
        names.addAll(List.of("The real manual-board UI receives a completed engine recommendation", "The displayed hint is a legal move from the displayed game frame",
                "The actual analysis board is laid out", "The actual device frame shows the board and rendered engine analysis"));
        return List.copyOf(names);
    }
}
