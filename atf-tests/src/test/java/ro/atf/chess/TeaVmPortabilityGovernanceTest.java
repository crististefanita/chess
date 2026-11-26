package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class TeaVmPortabilityGovernanceTest {
    private static final Pattern PROVIDER_LOOKUP = Pattern.compile(
            "(?:^\\s*import\\s+(?:static\\s+)?java\\.security\\..*;)|"
                    + "\\b(?:java\\.security\\.)?MessageDigest\\s*\\.\\s*getInstance\\s*\\(|"
                    + "\\b(?:java\\.lang\\.)?ClassLoader\\s*\\.\\s*(?:loadClass|getSystemClassLoader)\\s*\\(|"
                    + "\\b(?:java\\.util\\.)?ServiceLoader\\s*\\.\\s*load\\s*\\(");
    private static final Pattern JAVA_21_SEQUENCE_ACCESS = Pattern.compile("\\.\\s*(?:getFirst|getLast)\\s*\\(");

    @Test void portableCoreContainsNoDynamicSecurityProviderLookup(Checks check) throws IOException {
        var root = ProjectRoot.locate();
        check.same("TeaVM-reachable chess core does not use provider or class-loader lookup",
                List.of(), violations(root.resolve("chess-core/src/main/java")));
        check.same("TeaVM Java 17 sources avoid Java 21 SequencedCollection methods",
                List.of(), apiViolations(root.resolve("chess-core/src/main/java")));
        String proof = Files.readString(root.resolve("chess-core/src/main/java/ro/atf/chess/PawnPushProof.java"));
        String review = Files.readString(root.resolve("chess-core/src/main/java/ro/atf/chess/GameReviewCodec.java"));
        check.same("Hard proof IDs use the portable SHA-256 implementation", true, proof.contains("PortableSha256.hex("));
        check.same("Game Review checkpoint IDs use the portable SHA-256 implementation", true,
                review.contains("PortableSha256.digest("));
    }

    @Test void governanceRejectsProviderAndClassLoaderRegressions(@TempDir Path folder, Checks check) throws IOException {
        Path source = folder.resolve("ro/atf/chess/UnsafeDigest.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package ro.atf.chess;\n"
                + "import java.security.MessageDigest;\n"
                + "final class UnsafeDigest { Object digest() throws Exception { return MessageDigest.getInstance(\"SHA-256\"); } }\n");
        check.same("The regression fixture identifies the forbidden provider lookup and source line",
                List.of("ro/atf/chess/UnsafeDigest.java:2", "ro/atf/chess/UnsafeDigest.java:3"), violations(folder));
    }

    @Test void governanceRejectsCollectionMethodsOutsideTheTeaVmJava17Target(@TempDir Path folder, Checks check) throws IOException {
        Path source = folder.resolve("ro/atf/chess/UnsupportedSequenceApi.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package ro.atf.chess;" + System.lineSeparator()
                + "final class UnsupportedSequenceApi { Object first(java.util.List<?> values) { return values.getFirst(); } }");
        check.same("The compatibility fixture identifies the unsupported API and source line",
                List.of("ro/atf/chess/UnsupportedSequenceApi.java:2"), apiViolations(folder));
    }

    private static List<String> violations(Path sourceRoot) throws IOException {
        var found = new ArrayList<String>();
        if (!Files.isDirectory(sourceRoot)) throw new IOException("Portable core source root is missing: " + sourceRoot);
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                var lines = Files.readAllLines(file);
                for (int index = 0; index < lines.size(); index++) {
                    if (PROVIDER_LOOKUP.matcher(lines.get(index)).find()) {
                        found.add(sourceRoot.relativize(file).toString().replace('\\', '/') + ":" + (index + 1));
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    private static List<String> apiViolations(Path sourceRoot) throws IOException {
        var found = new ArrayList<String>();
        if (!Files.isDirectory(sourceRoot)) throw new IOException("Portable core source root is missing: " + sourceRoot);
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                var lines = Files.readAllLines(file);
                for (int index = 0; index < lines.size(); index++) {
                    if (JAVA_21_SEQUENCE_ACCESS.matcher(lines.get(index)).find()) {
                        found.add(sourceRoot.relativize(file).toString().replace('\\', '/') + ":" + (index + 1));
                    }
                }
            }
        }
        return List.copyOf(found);
    }
}
