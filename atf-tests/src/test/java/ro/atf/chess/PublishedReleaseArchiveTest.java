package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.release.PublishedReleaseArchive;
import ro.atf.chess.release.ReleaseEvidence;
import ro.atf.chess.release.ReleaseGate;
import ro.atf.chess.release.ReleaseRag;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class PublishedReleaseArchiveTest {
    private static final String VERSION = "0.17.1";
    private static final String PREFIX = "release/" + VERSION + "/reports/";
    private static final String ASSET = "https://github.com/example/chess/releases/download/v0.17.1/ChessLab-0.17.1.apk";
    private final ObjectMapper json = new ObjectMapper();
    @TempDir Path folder;
    private Path source;
    private Path destination;
    private Path study;
    private Path apk;
    private String producer;
    private String publication;

    @BeforeEach void fixture() throws Exception {
        source = Files.createDirectory(folder.resolve("source"));
        destination = Files.createDirectory(folder.resolve("experimental checkout")).resolve("release");
        study = Files.createDirectory(folder.resolve("studies"));
        apk = study.resolve("ChessLab-0.17.1.apk");
        try (var zip = new ZipOutputStream(Files.newOutputStream(apk))) {
            for (String name : List.of("AndroidManifest.xml", "classes.dex")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(name.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        git("init", "-q");
        git("config", "user.name", "Release fixture");
        git("config", "user.email", "fixture@example.invalid");
        write("README.md", "# Chess Lab\n\n## Game\n\n- [APK](" + ASSET + ")\n"
                + "- [Web](https://example.github.io/chess/)\n\n## Published Release\n\n"
                + "- [Release](https://github.com/example/chess/releases/tag/v0.17.1)\n");
        producer = commit();
        git("tag", "v0.17.1");
        writeJson("release/index.json", Map.of("currentVersion", VERSION, "previousVersion", "0.16.2", "versions", List.of(VERSION, "0.16.2")));
        write("release/README.md", "# Releases\n");
        for (String version : List.of(VERSION, "0.16.2")) {
            write("release/" + version + "/README.md", "# " + version + "\n");
            write("release/" + version + "/reports/README.md", "# Evidence\n");
        }
        write(PREFIX + "components.html", "<html>\r\n  <details>original evidence</details>\r\n</html>\r\n");
        write(PREFIX + "android-game-review.html", "<html>native evidence</html>\n");
        write(PREFIX + "game-review-execution.json", "{}\n");
        write(PREFIX + "rag.txt", "Advisory selection\n");
        writeJson(PREFIX + "qualification.json", Map.of("version", VERSION, "qualificationGate", "PASS", "sourceCommit", producer,
                "apkBytes", Files.size(apk), "apkSha256", ReleaseEvidence.digest(apk),
                "componentExtentSha256", ReleaseEvidence.digest(source.resolve(PREFIX + "components.html"))));
        writeJson(PREFIX + "github-release.json", Map.of("draft", false, "tag", "v" + VERSION, "targetCommit", producer,
                "bytes", Files.size(apk), "digest", "sha256:" + ReleaseEvidence.digest(apk), "assetUrl", ASSET));
        for (String receipt : List.of("public-download.json", "distribution-verification.json")) {
            writeJson(PREFIX + receipt, Map.of("status", "PASS", "httpStatus", 200, "zipCrc", "PASS", "targetCommit", producer,
                    "assetUrl", ASSET, "bytes", Files.size(apk), "sha256", ReleaseEvidence.digest(apk)));
        }
        publication = commit();
    }

    @Test void copiesOnlyThePublishedEditionAndPreservesExperimentalSources(Checks check) throws Exception {
        Files.createDirectories(destination.resolve("0.14.1"));
        Files.writeString(destination.resolve("0.14.1/local-notes.txt"), "uncommitted notes");
        Path code = destination.getParent().resolve("uncommitted.java");
        Files.writeString(code, "parallel source");
        var result = sync();
        check.same("The newest published edition replaces the old local projection", "SYNCED", result.status());
        check.same("Only the newest edition is materialized", List.of(VERSION), directories(destination));
        check.same("Working source outside release remains untouched", "parallel source", Files.readString(code));
        check.same("Old uncommitted release notes remain recoverable outside the checkout", "uncommitted notes",
                Files.readString(result.backup().resolve("0.14.1/local-notes.txt")));
        check.same("Extent bytes survive without newline normalization", ReleaseEvidence.digest(source.resolve(PREFIX + "components.html")),
                ReleaseEvidence.digest(destination.resolve(VERSION + "/reports/components.html")));
        check.same("The locally installable APK is the qualified complete asset", ReleaseEvidence.digest(apk),
                ReleaseEvidence.digest(destination.resolve(VERSION + "/ChessLab-" + VERSION + ".apk")));
        check.same("An identical rerun performs no replacement", "UNCHANGED", sync().status());
        check.same("The installed projection verifies independently", "PASS", verify().status());
        ReleaseGate.main(new String[]{"local-delivery", source.toString(), publication, destination.toString(), apk.toString()});
    }

    @Test void verificationRejectsMissingChangedAndExtraArtifacts(Checks check) throws Exception {
        sync();
        Path report = destination.resolve(VERSION + "/reports/components.html");
        byte[] original = Files.readAllBytes(report);
        Files.delete(report);
        check.rejects("A missing Extent report prevents delivery completion", IOException.class, this::verify);
        check.rejects("The delivery CLI cannot complete with a missing report", IOException.class,
                () -> ReleaseGate.main(new String[]{"local-delivery", source.toString(), publication, destination.toString(), apk.toString()}));
        Files.writeString(report, "PASS");
        check.rejects("A substituted PASS report cannot pass byte verification", IOException.class, this::verify);
        Files.write(report, original);
        Path staleDirectory = Files.createDirectory(destination.resolve("0.14.1"));
        check.rejects("Even an empty old edition fails the current-only inventory", IOException.class, this::verify);
        Files.delete(staleDirectory);
        Files.writeString(destination.resolve("old-report.html"), "stale evidence");
        check.rejects("Loose evidence from an old edition fails verification", IOException.class, this::verify);
    }

    @Test void staleReadmeAndWrongPublishedTagCannotBeCopied(Checks check) throws Exception {
        write("README.md", Files.readString(source.resolve("README.md")).replace(ASSET, ASSET.replace("0.17.1", "0.16.2")));
        publication = commit();
        check.rejects("A publication README linked to another APK blocks synchronization", IOException.class, this::sync);
        check.same("No destination is installed from a stale README", false, Files.exists(destination));
        git("tag", "-f", "v0.17.1", publication);
        check.rejects("A retagged source cannot masquerade as the qualified producer", IOException.class, this::sync);
    }

    @Test void incompleteDownloadOrUnqualifiedReportBlocksReplacement(Checks check) throws Exception {
        writeJson(PREFIX + "public-download.json", Map.of("status", "PASS", "httpStatus", 404, "zipCrc", "PASS"));
        publication = commit();
        check.rejects("Authenticated success cannot replace the missing public download", IOException.class, this::sync);
        write(PREFIX + "components.html", "changed report");
        publication = commit();
        check.rejects("A report that no longer matches qualification is rejected", IOException.class, this::sync);
        check.same("Rejected evidence leaves the target absent", false, Files.exists(destination));
    }

    @Test void damagedApkAndUnsafePerimetersNeverReplaceTheTarget(Checks check) throws Exception {
        Files.writeString(apk, "404: Not Found");
        check.rejects("A truncated download cannot become the installable APK", IOException.class, this::sync);
        check.rejects("A source checkout cannot be its own destination", IOException.class,
                () -> PublishedReleaseArchive.synchronize(source, publication, source.resolve("release"), study, apk));
        check.rejects("A branch name cannot replace a pinned publication SHA", IOException.class,
                () -> PublishedReleaseArchive.synchronize(source, "main", destination, study, apk));
        check.rejects("Staging inside the destination cannot endanger local code", IOException.class,
                () -> PublishedReleaseArchive.synchronize(source, publication, destination, destination.getParent(), apk));
    }

    @Test void publishedRagContextIsPartOfTheRequiredArchive(Checks check) throws Exception {
        git("rm", "-q", PREFIX + "rag.txt");
        publication = commit();
        check.rejects("An edition without its original RAG context is incomplete", IOException.class, this::sync);
        check.same("Incomplete RAG evidence never replaces the local release", false, Files.exists(destination));
    }

    @Test void ragSelectsLocalDeliveryVerificationForPublicationChanges(Checks check) throws Exception {
        var result = ReleaseRag.inspect(ProjectRoot.locate(), List.of("release/index.json", "release-tools/scripts/sync-published-release.ps1",
                "release-tools/src/main/java/ro/atf/chess/release/PublishedReleaseArchive.java"));
        check.same("Every delivery change cites the local synchronization gate", true,
                result.citations().stream().anyMatch(row -> row.check().contains("PublishedReleaseArchiveTest") && row.check().contains("PublishedReleaseArchive verify")));
    }

    private PublishedReleaseArchive.Result sync() throws IOException {
        return PublishedReleaseArchive.synchronize(source, publication, destination, study, apk);
    }

    private PublishedReleaseArchive.Result verify() throws IOException {
        return PublishedReleaseArchive.verify(source, publication, destination, apk);
    }

    private List<String> directories(Path root) throws IOException {
        try (var files = Files.list(root)) { return files.filter(Files::isDirectory).map(path -> path.getFileName().toString()).sorted().toList(); }
    }

    private void write(String name, String text) throws IOException {
        Path path = source.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }

    private void writeJson(String name, Object value) throws IOException { write(name, json.writeValueAsString(value)); }

    private String commit() throws Exception {
        git("add", ".");
        git("commit", "-qm", "publication fixture");
        return git("rev-parse", "HEAD").strip();
    }

    private String git(String... args) throws Exception {
        var command = new java.util.ArrayList<>(List.of("git", "-C", source.toString(), "-c", "core.autocrlf=false"));
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IOException(output);
        return output;
    }
}
