package ro.atf.chess.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Materializes published evidence independently of an experimental working tree. */
public final class PublishedReleaseArchive {
    public record Result(String status, String version, String sourceCommit, int files, Path destination, Path backup) { }
    private record Snapshot(String version, String commit, Map<String, byte[]> files) { }

    private PublishedReleaseArchive() { }

    public static Result synchronize(Path repository, String commit, Path destination, Path workspace, Path apk) throws IOException {
        Path source = repository.toRealPath();
        Path target = target(source, destination);
        Path work = workspace.toRealPath();
        require(!work.startsWith(source) && !source.startsWith(work) && !work.startsWith(target) && !target.startsWith(work),
                "Staging must be outside both checkouts");
        Snapshot snapshot = snapshot(source, commit, apk);
        Map<String, String> before = inventory(target);
        Map<String, String> expected = fingerprints(snapshot.files());
        if (before.equals(expected)) return result("UNCHANGED", snapshot, target, null);
        Path stage = Files.createTempDirectory(work, "published-release-");
        Path next = Files.createDirectory(stage.resolve("release"));
        for (var file : snapshot.files().entrySet()) {
            Path path = next.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, file.getValue());
        }
        require(inventory(next).equals(expected), "Staged release changed before installation");
        require(inventory(target).equals(before), "Local release changed concurrently; nothing replaced");
        Path backup = null;
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            backup = stage.resolve("previous-release");
            Files.move(target, backup);
        }
        try {
            Files.move(next, target);
        } catch (IOException failure) {
            if (backup != null) {
                try { Files.move(backup, target); }
                catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
        require(inventory(target).equals(expected), "Installed release changed; previous release remains in " + backup);
        return result("SYNCED", snapshot, target, backup);
    }

    public static Result verify(Path repository, String commit, Path destination, Path apk) throws IOException {
        Path source = repository.toRealPath();
        Path target = target(source, destination);
        Snapshot snapshot = snapshot(source, commit, apk);
        require(inventory(target).equals(fingerprints(snapshot.files())), "Local release is missing, stale, modified or contains extra artifacts");
        return result("PASS", snapshot, target, null);
    }

    private static Result result(String status, Snapshot snapshot, Path target, Path backup) {
        return new Result(status, snapshot.version(), snapshot.commit(), snapshot.files().size(), target, backup);
    }

    private static Path target(Path source, Path destination) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        require(target.getFileName().toString().equals("release"), "Destination must be a release directory");
        require(!target.startsWith(source) && !source.startsWith(target), "Destination overlaps the source checkout");
        Path cursor = target;
        while (cursor != null) {
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(cursor) && cursor.toRealPath().equals(cursor), "Linked destination is not supported");
            }
            cursor = cursor.getParent();
        }
        require(Files.isDirectory(target.getParent()), "Destination checkout must already exist");
        return target;
    }

    private static Snapshot snapshot(Path source, String commit, Path apk) throws IOException {
        require(commit.matches("[a-f0-9]{40}"), "Pin an immutable publication commit");
        var files = new TreeMap<String, byte[]>();
        String tree = GitTestChanges.line(GitTestChanges.command(source, "ls-tree", "-rz", commit, "--", "release/"));
        for (String entry : tree.split("\0")) {
            if (entry.isEmpty()) continue;
            String[] fields = entry.split("\t", 2);
            require(fields.length == 2 && fields[0].startsWith("100644 blob "), "Release contains non-regular Git entries");
            String name = fields[1].substring("release/".length());
            require(!name.contains("\\") && !List.of(name.split("/")).contains(".."), "Invalid release path");
            files.put(name, GitTestChanges.command(source, new byte[0], "show", commit + ":release/" + name));
        }
        Map<String, Object> catalog = document(files, "index.json");
        String version = text(catalog, "currentVersion");
        String previous = text(catalog, "previousVersion");
        require(version.matches("0\\.\\d+\\.\\d+") && previous.matches("0\\.\\d+\\.\\d+") && !version.equals(previous), "Invalid retained releases");
        require(List.of(version, previous).equals(catalog.get("versions")), "Retained catalog is not current followed by previous");
        require(files.containsKey("README.md"), "Missing release catalog documentation");
        for (String name : files.keySet()) {
            require(name.equals("index.json") || name.equals("README.md") || name.startsWith(version + "/") || name.startsWith(previous + "/"),
                    "Unretained release artifact: " + name);
        }
        for (String retained : List.of(version, previous)) {
            require(files.containsKey(retained + "/README.md") && files.containsKey(retained + "/reports/README.md"), "Missing retained release documentation");
        }
        String prefix = version + "/reports/";
        Map<String, Object> qualified = document(files, prefix + "qualification.json");
        String producer = text(qualified, "sourceCommit");
        String hash = text(qualified, "apkSha256");
        long bytes = number(qualified, "apkBytes");
        require("PASS".equals(qualified.get("qualificationGate")) && version.equals(qualified.get("version")), "Release is not qualified");
        return completed(source, commit, apk, files, version, prefix, qualified, producer, hash, bytes);
    }

    private static Snapshot completed(Path source, String commit, Path apk, Map<String, byte[]> files, String version, String prefix,
                                      Map<String, Object> qualified, String producer, String hash, long bytes) throws IOException {
        require(text(qualified, "componentExtentSha256").equals(digest(required(files, prefix + "components.html"))), "Component Extent fingerprint differs");
        required(files, prefix + "android-game-review.html");
        required(files, prefix + "game-review-execution.json");
        required(files, prefix + "rag.txt");
        Map<String, Object> github = document(files, prefix + "github-release.json");
        require(Boolean.FALSE.equals(github.get("draft")) && producer.equals(github.get("targetCommit")), "Release is unpublished or has a different producer");
        require(bytes == number(github, "bytes") && ("sha256:" + hash).equals(github.get("digest")), "GitHub asset identity differs");
        require(("v" + version).equals(github.get("tag")), "GitHub release tag differs");
        String tagCommit = GitTestChanges.line(GitTestChanges.command(source, "rev-parse", "refs/tags/v" + version + "^{commit}"));
        require(producer.equals(tagCommit), "Published tag differs from the qualified source");
        String asset = text(github, "assetUrl");
        String readme = new String(GitTestChanges.command(source, new byte[0], "show", commit + ":README.md"), StandardCharsets.UTF_8);
        require(ReleaseEvidence.gameLinks(readme).get(1).equals(asset), "Publication README points to a different APK");
        for (String receipt : List.of("public-download.json", "distribution-verification.json")) {
            Map<String, Object> download = document(files, prefix + receipt);
            require("PASS".equals(download.get("status")) && number(download, "httpStatus") == 200 && "PASS".equals(download.get("zipCrc")), "Download is not complete");
            require(producer.equals(download.get("targetCommit")) && asset.equals(download.get("assetUrl")) && hash.equals(download.get("sha256"))
                    && bytes == number(download, "bytes"), "Download receipt identifies another APK");
        }
        var candidate = new ReleaseEvidence.Candidate(version, producer, bytes, hash);
        ReleaseEvidence.apk(candidate, apk);
        byte[] apkBytes = Files.readAllBytes(apk);
        require(apkBytes.length == bytes && digest(apkBytes).equals(hash), "APK changed during synchronization");
        files.keySet().removeIf(path -> !path.startsWith(version + "/"));
        String index = "{\n  \"schemaVersion\": 2,\n  \"kind\": \"PUBLISHED_RELEASE_CHECKOUT\",\n  \"currentVersion\": \"" + version
                + "\",\n  \"versions\": [\"" + version + "\"]\n}\n";
        files.put("index.json", index.getBytes(StandardCharsets.UTF_8));
        String localReadme = "# Chess Lab · " + version + "\n\n- [APK](" + version + "/ChessLab-" + version + ".apk)\n"
                + "- [Extent](" + version + "/reports/README.md)\n- [RAG](" + version + "/reports/rag.txt)\n";
        files.put("README.md", localReadme.getBytes(StandardCharsets.UTF_8));
        files.put(version + "/ChessLab-" + version + ".apk", apkBytes);
        var receipt = new LinkedHashMap<String, Object>();
        receipt.put("kind", "PUBLISHED_RELEASE_CHECKOUT");
        receipt.put("publicationCommit", commit);
        receipt.put("currentVersion", version);
        receipt.put("qualifiedSourceCommit", producer);
        receipt.put("apkSha256", hash);
        receipt.put("inventory", fingerprints(files));
        files.put("local-sync.yaml", new Yaml().dump(receipt).getBytes(StandardCharsets.UTF_8));
        return new Snapshot(version, commit, Map.copyOf(files));
    }

    private static Map<String, Object> document(Map<String, byte[]> files, String path) throws IOException {
        Object value = new Yaml(new SafeConstructor(new LoaderOptions())).load(new String(required(files, path), StandardCharsets.UTF_8));
        require(value instanceof Map<?, ?>, "Invalid release document: " + path);
        var result = new LinkedHashMap<String, Object>();
        ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static byte[] required(Map<String, byte[]> files, String name) throws IOException {
        byte[] bytes = files.get(name);
        require(bytes != null, "Missing published artifact: " + name);
        return bytes;
    }

    private static String text(Map<String, Object> value, String key) throws IOException {
        require(value.get(key) instanceof String, "Missing text field: " + key);
        return (String) value.get(key);
    }

    private static long number(Map<String, Object> value, String key) throws IOException {
        require(value.get(key) instanceof Number, "Missing numeric field: " + key);
        return ((Number) value.get(key)).longValue();
    }

    private static Map<String, String> inventory(Path root) throws IOException {
        var result = new TreeMap<String, String>();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return result;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                require(!Files.isSymbolicLink(path) && path.toRealPath().equals(path.toAbsolutePath().normalize()), "Linked local artifact: " + path);
                if (Files.isDirectory(path)) {
                    try (var children = Files.list(path)) {
                        if (!path.equals(root) && children.findAny().isEmpty()) {
                            result.put(root.relativize(path).toString().replace('\\', '/') + "/", "EMPTY_DIRECTORY");
                        }
                    }
                    continue;
                }
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Non-regular local artifact: " + path);
                result.put(root.relativize(path).toString().replace('\\', '/'), ReleaseEvidence.digest(path));
            }
        }
        return result;
    }

    private static Map<String, String> fingerprints(Map<String, byte[]> files) {
        var result = new TreeMap<String, String>();
        files.forEach((path, bytes) -> result.put(path, digest(bytes)));
        return result;
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void require(boolean valid, String message) throws IOException {
        if (!valid) throw new IOException(message);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 6 || !List.of("sync", "verify").contains(args[0])) {
            throw new IllegalArgumentException("Expected sync|verify <Git source> <publication SHA> <destination release> <external workspace> <verified APK>");
        }
        Result result = args[0].equals("sync")
                ? synchronize(Path.of(args[1]), args[2], Path.of(args[3]), Path.of(args[4]), Path.of(args[5]))
                : verify(Path.of(args[1]), args[2], Path.of(args[3]), Path.of(args[5]));
        System.out.printf("%s version=%s publication=%s files=%d destination=%s backup=%s%n",
                result.status(), result.version(), result.sourceCommit(), result.files(), result.destination(), result.backup());
    }
}
