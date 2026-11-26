package ro.atf.chess.android;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ro.atf.chess.ExternalEngineAnalysis;
import ro.atf.chess.StockfishCoaching;
import ro.atf.chess.StockfishTransport;

/** The APK's pinned, native Stockfish process. Each coaching owner has its own lifecycle. */
final class AndroidStockfish {
    private static final String SOURCE = "0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6";
    private static final String NETWORK = "nn-61e7af4bb97d.nnue";
    private static final String NETWORK_SHA = "61e7af4bb97d51eeeb25d322916f86513b5cd3a827ce189c98c6e31946f99e5b";
    private static final long NETWORK_BYTES = 1_166_381L;
    private static final long NODES = 200_000;
    private static final String OPTIONS = "{\"threads\":1,\"hashMb\":32,\"nodes\":200000,\"limitStrength\":false}";
    private static final Object DATA_LOCK = new Object();
    private static final Object JOURNAL_LOCK = new Object();
    private static final int MAX_RECEIPTS = 64;

    private AndroidStockfish() { }

    /** Construction is lazy; asset verification, process startup and journal I/O run on the caller's search worker. */
    static StockfishCoaching coaching(Context context) {
        Context app = context.getApplicationContext();
        return new StockfishCoaching(() -> open(app), (phase, result) -> record(app, phase, result));
    }

    private static StockfishTransport open(Context context) {
        Process process = null;
        try {
            JSONObject manifest;
            try (InputStream in = context.getAssets().open("stockfish/manifest.json")) {
                byte[] bytes = readBounded(in, 1_048_576);
                manifest = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            }
            if (!"BUILT_AND_PINNED".equals(manifest.optString("buildStatus")))
                throw new IOException("Android Stockfish Lite is NOT_BUILT; native binaries remain pending");
            if (manifest.getInt("schema") != 1 || !SOURCE.equals(manifest.getString("sourceCommit"))
                    || !"19.0.1".equals(manifest.getString("version")) || !"lite".equals(manifest.getString("flavor")) || manifest.getInt("minSdk") != 26
                    || manifest.getInt("elfPageAlignment") != 16_384) {
                throw new IOException("Unexpected packaged Stockfish provenance");
            }
            var network = manifest.getJSONObject("network");
            if (!NETWORK.equals(network.getString("path")) || !NETWORK_SHA.equals(network.getString("sha256"))
                    || network.getLong("bytes") != NETWORK_BYTES) throw new IOException("Unexpected Lite Stockfish network");
            File executable = new File(context.getApplicationInfo().nativeLibraryDir, "libstockfish.so");
            if (!executable.isFile() || !executable.canExecute()) throw new IOException("Packaged Stockfish executable is unavailable");
            String executableSha = sha(executable);
            JSONObject binary = null;
            JSONArray binaries = manifest.getJSONArray("binaries");
            for (int index = 0; index < binaries.length(); index++) {
                JSONObject candidate = binaries.getJSONObject(index);
                if (executableSha.equals(candidate.getString("sha256")) && executable.length() == candidate.getLong("bytes")) {
                    if (binary != null) throw new IOException("Ambiguous Stockfish binary identity");
                    binary = candidate;
                }
            }
            if (binary == null) throw new IOException("Stockfish executable checksum mismatch");
            String abi = binary.getString("abi");
            if (!Arrays.asList("arm64-v8a", "x86_64", "armeabi-v7a").contains(abi)) throw new IOException("Unsupported Stockfish ABI");
            String providerID = "stockfish-19.0.1-android-lite-" + abi;
            if (!providerID.equals(binary.getString("providerID"))) throw new IOException("Stockfish provider identity mismatch");
            File data = prepareNetwork(context);
            var provider = new ExternalEngineAnalysis.Provider(providerID, "19.0.1", executableSha, SOURCE, OPTIONS);
            process = new ProcessBuilder(executable.getAbsolutePath()).directory(context.getFilesDir()).redirectErrorStream(true).start();
            return StockfishTransport.process(process, provider, NODES, 3).network(data.getAbsolutePath());
        } catch (IOException | JSONException | RuntimeException failed) {
            if (process != null) process.destroy();
            throw new IllegalStateException("Cannot start the verified Android Stockfish engine", failed);
        }
    }

    private static File prepareNetwork(Context context) throws IOException {
        synchronized (DATA_LOCK) {
            File directory = new File(context.getFilesDir(), "stockfish");
            ensureDirectory(directory);
            File target = new File(directory, NETWORK);
            if (target.isFile() && target.length() == NETWORK_BYTES && NETWORK_SHA.equals(sha(target))) return target;
            var atomic = new AtomicFile(target);
            FileOutputStream out = null;
            try (InputStream in = context.getAssets().open("stockfish/" + NETWORK)) {
                out = atomic.startWrite();
                MessageDigest digest = digest();
                byte[] buffer = new byte[65_536];
                long count = 0;
                for (int read; (read = in.read(buffer)) >= 0;) {
                    count += read;
                    if (count > NETWORK_BYTES) throw new IOException("Stockfish network exceeds its pin");
                    out.write(buffer, 0, read); digest.update(buffer, 0, read);
                }
                if (count != NETWORK_BYTES || !NETWORK_SHA.equals(hex(digest.digest()))) throw new IOException("Stockfish network checksum mismatch");
                atomic.finishWrite(out); out = null;
            } finally { if (out != null) atomic.failWrite(out); }
            if (target.length() != NETWORK_BYTES || !NETWORK_SHA.equals(sha(target))) throw new IOException("Cannot retain the verified Stockfish network");
            return target;
        }
    }

    private static void record(Context context, String phase, StockfishTransport.Search result) {
        synchronized (JOURNAL_LOCK) {
            try {
                var batch = result.batch();
                String key = sha((batch.context().contextKey() + "\n" + phase + "\n" + batch.provider().buildSha256()).getBytes(StandardCharsets.UTF_8));
                String receiptID = UUID.randomUUID().toString();
                var receipt = new JSONObject();
                receipt.put("schema", 1); receipt.put("scope", phase.startsWith("reuse:")
                        ? "ANDROID_STOCKFISH_CACHE_REVALIDATION" : "ACTUAL_ANDROID_STOCKFISH_UCI_SEARCH");
                receipt.put("receiptID", receiptID);
                receipt.put("contextSha256", sha(batch.context().json().getBytes(StandardCharsets.UTF_8)));
                receipt.put("createdAtMillis", System.currentTimeMillis()); receipt.put("phase", phase);
                receipt.put("networkSha256", NETWORK_SHA);
                receipt.put("context", new JSONObject(batch.context().json()));
                receipt.put("batch", new JSONObject(batch.json()));
                var completed = new JSONArray();
                for (var iteration : result.completed().values()) completed.put(new JSONObject(iteration.json()));
                receipt.put("completedDepthBatches", completed); receipt.put("bestmove", result.bestmove());
                receipt.put("rawUCITranscript", result.transcript());
                receipt.put("transcriptSha256", sha(result.transcript().getBytes(StandardCharsets.UTF_8)));
                byte[] bytes = receipt.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > 8 * 1_048_576) throw new IOException("Stockfish audit receipt exceeds its bound");
                File directory = new File(context.getFilesDir(), "stockfish-audit");
                ensureDirectory(directory);
                File target = new File(directory, key + "-" + receiptID + ".json");
                var atomic = new AtomicFile(target);
                FileOutputStream out = null;
                try {
                    out = atomic.startWrite(); out.write(bytes); atomic.finishWrite(out); out = null;
                } finally { if (out != null) atomic.failWrite(out); }
                if (target.length() != bytes.length || !sha(bytes).equals(sha(target))) throw new IOException("Cannot retain the Stockfish audit receipt");
                File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
                if (files == null) throw new IOException("Cannot enumerate the Stockfish audit journal");
                Arrays.sort(files, Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
                for (int index = 0; index < files.length - MAX_RECEIPTS; index++) {
                    if (!files[index].delete()) throw new IOException("Cannot bound the Stockfish audit journal");
                }
            } catch (IOException | JSONException failed) { throw new IllegalStateException("Cannot save the actual Stockfish analysis receipt", failed); }
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create Stockfish data directory");
    }

    private static byte[] readBounded(InputStream in, int max) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = in.read(buffer)) >= 0;) {
            if (out.size() + read > max) throw new IOException("Stockfish metadata exceeds its bound");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String sha(File file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65_536];
            for (int read; (read = in.read(buffer)) >= 0;) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }
    private static String sha(byte[] bytes) { return hex(digest().digest(bytes)); }
    private static String hex(byte[] bytes) {
        var out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        return out.toString();
    }
}
