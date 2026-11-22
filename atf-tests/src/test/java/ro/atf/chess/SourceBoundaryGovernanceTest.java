package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.release.ReleaseRag;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class SourceBoundaryGovernanceTest {
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.$*]+)\\s*;.*$");
    private static final Map<String, List<String>> FORBIDDEN = Map.of(
            "chess-core", List.of("android.", "ro.atf.chess.android.", "ro.atf.chess.web.", "org.junit.", "atf."),
            "web", List.of("android.", "ro.atf.chess.android.", "org.junit.", "atf."),
            "android/app", List.of("ro.atf.chess.web.", "org.junit.", "atf."),
            "release-tools", List.of("android.", "ro.atf.chess.android.", "ro.atf.chess.web.", "org.junit.", "atf."));

    @TempDir Path folder;

    @Test void liveProductionSourcesRespectTheDocumentedPlatformBoundary(Checks check) throws IOException {
        Path root = ProjectRoot.locate();
        check.same("Every governed production source root exists and stays within its boundary", List.of(), violations(root));
        check.same("The code-quality guide and index route to the executable owner", List.of(), missingGuideRoutes(root));
        String expectedHeading = Files.readString(root.resolve("docs/code-quality.md")).startsWith("# Contractul de calitate a codului")
                ? "## Guvernanță executabilă" : "## Executable governance";
        var citations = ReleaseRag.retrieve(root, List.of("docs/code-quality.md"));
        check.same("RAG resolves the quality guide in the current documentation language", List.of(expectedHeading),
                citations.stream().map(ReleaseRag.Citation::heading).toList());
    }

    @Test void forbiddenImportsFailInNegativeFixturesAndDomainImportsRemainAllowed(Checks check) throws IOException {
        Path root = folder;
        fixture(root, "chess-core", "ro/atf/chess/LeakyCore.java", "package ro.atf.chess;\nimport android.view.View;\n");
        fixture(root, "web", "ro/atf/chess/LeakyWeb.java", "package ro.atf.chess;\nimport ro.atf.chess.android.BoardView;\n");
        fixture(root, "android/app", "ro/atf/chess/android/LeakyAndroid.java", "package ro.atf.chess.android;\nimport ro.atf.chess.web.WebChessGame;\n");
        fixture(root, "release-tools", "ro/atf/chess/release/LeakyRelease.java", "package ro.atf.chess.release;\nimport atf.reporting.contract.AtfAssertion;\n");
        fixture(root, "release-tools", "ro/atf/chess/release/LeakyTest.java", "package ro.atf.chess.release;\nimport org.junit.jupiter.api.Test;\n");
        fixture(root, "chess-core", "ro/atf/chess/AllowedCore.java",
                "package ro.atf.chess;\nimport java.util.List;\nimport ro.atf.chess.ChessPosition;\n");

        var actual = violations(root);
        check.same("Each platform and test-only dependency is rejected with its source location", List.of(
                "android/app/src/main/java/ro/atf/chess/android/LeakyAndroid.java:2 forbidden import ro.atf.chess.web.WebChessGame",
                "chess-core/src/main/java/ro/atf/chess/LeakyCore.java:2 forbidden import android.view.View",
                "release-tools/src/main/java/ro/atf/chess/release/LeakyRelease.java:2 forbidden import atf.reporting.contract.AtfAssertion",
                "release-tools/src/main/java/ro/atf/chess/release/LeakyTest.java:2 forbidden import org.junit.jupiter.api.Test",
                "web/src/main/java/ro/atf/chess/LeakyWeb.java:2 forbidden import ro.atf.chess.android.BoardView"), actual);
    }

    @Test void ragRejectsAQualityGuideWithoutItsExecutableGovernanceAnchor(Checks check) throws IOException {
        Path root = folder.resolve("missing-rag-anchor");
        Path guide = root.resolve("docs/code-quality.md");
        Files.createDirectories(guide.getParent());
        Files.writeString(guide, "# Code quality\n\n## Unrelated section\n");
        check.rejects("The quality-guide route cannot borrow another heading", IOException.class,
                () -> ReleaseRag.retrieve(root, List.of("docs/code-quality.md")));
    }

    private static List<String> missingGuideRoutes(Path root) throws IOException {
        String guide = Files.readString(root.resolve("docs/code-quality.md"));
        String index = Files.readString(root.resolve("docs/README.md"));
        boolean romanian = guide.startsWith("# Contractul de calitate a codului");
        var required = romanian
                ? List.of("chess-core", "Android", "Web", "release-tools", "SourceBoundaryGovernanceTest",
                        "DocumentationGovernanceTest", "ChessGovernanceSuite", "## Proprietatea și direcția dependențelor",
                        "## Guvernanță executabilă", "## Responsabilitatea feedback-ului")
                : List.of("chess-core", "Android", "Web", "release-tools", "SourceBoundaryGovernanceTest",
                        "DocumentationGovernanceTest", "ChessGovernanceSuite", "## Ownership and dependency direction",
                        "## Executable governance", "## Feedback ownership");
        var missing = new ArrayList<String>();
        if (!index.contains("](code-quality.md)")) missing.add("docs/README.md does not route to code-quality.md");
        for (String text : required) if (!guide.contains(text)) missing.add("docs/code-quality.md missing: " + text);
        return List.copyOf(missing);
    }

    private static List<String> violations(Path root) throws IOException {
        var found = new TreeSet<String>();
        for (var entry : FORBIDDEN.entrySet()) {
            Path source = root.resolve(entry.getKey()).resolve("src/main/java");
            if (!Files.isDirectory(source)) {
                found.add(entry.getKey() + "/src/main/java missing");
                continue;
            }
            try (Stream<Path> paths = Files.walk(source)) {
                for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                    var lines = Files.readAllLines(file);
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    for (int index = 0; index < lines.size(); index++) {
                        var imported = IMPORT.matcher(lines.get(index));
                        if (!imported.matches()) continue;
                        String name = imported.group(1);
                        if (entry.getValue().stream().anyMatch(name::startsWith)) {
                            found.add(relative + ":" + (index + 1) + " forbidden import " + name);
                        }
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    private static void fixture(Path root, String module, String file, String source) throws IOException {
        Path target = root.resolve(module).resolve("src/main/java").resolve(file);
        Files.createDirectories(target.getParent());
        Files.writeString(target, source);
    }
}
