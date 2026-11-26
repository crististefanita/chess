package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathNodes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;
import ro.atf.chess.release.ReleaseRag;
import ro.atf.chess.release.ReleaseWorkspace;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class ReleaseModuleLayoutTest {
    @TempDir Path folder;

    @Test void reactorConsumesOneIndependentReleaseArtifact(Checks check) throws Exception {
        Path root = ProjectRoot.locate();
        check.same("The reactor, production dependency and module boundary agree", List.of(), topology(
                Files.readString(root.resolve("pom.xml")), Files.readString(root.resolve("atf-tests/pom.xml")),
                Files.readString(root.resolve("release-tools/pom.xml"))));
        String origin = ReleaseRag.class.getProtectionDomain().getCodeSource().getLocation().toString();
        check.same("Release classes come from their module or its JAR", true,
                origin.contains("release-tools") && !origin.contains("atf-tests/target/classes"));
        check.same("A previous consumer build cannot shadow the extracted module", false,
                Files.exists(root.resolve("atf-tests/target/classes/ro/atf/chess/release")));
        check.same("Legacy root signing is absent", false, Files.exists(root.resolve(".atf-signing")));
        for (String name : List.of("Initialize-J7Signing.ps1", "verify-signing-continuity.ps1",
                "verify-authenticated-download.ps1", "sync-published-release.ps1")) {
            String adapter = Files.readString(root.resolve("release-tools/scripts").resolve(name));
            check.same("Delivery adapter resolves its own workspace: " + name, true, adapter.contains("Get-ChessReleaseWorkspace"));
            check.same("Delivery adapter has no consumer-class fallback: " + name, false, adapter.contains("atf-tests/target/classes"));
        }
    }

    @Test void duplicateSourcesMissingDependencyAndGameCouplingFail(Checks check) throws Exception {
        Path root = ProjectRoot.locate();
        String reactor = Files.readString(root.resolve("pom.xml"));
        String consumer = Files.readString(root.resolve("atf-tests/pom.xml"));
        String module = Files.readString(root.resolve("release-tools/pom.xml"));
        check.same("Removing the module loses the documented reactor", true,
                topology(reactor.replace("<module>release-tools</module>", ""), consumer, module).contains("reactor"));
        check.same("A test-scoped module cannot supply production CLI classes", true,
                topology(reactor, consumer.replace("chess-release-tools</artifactId>", "chess-release-tools</artifactId><scope>test</scope>"), module).contains("dependency"));
        check.same("Adding the old source merger is rejected even with the JAR present", true,
                topology(reactor, consumer.replace("<plugins>", "<plugins><plugin><artifactId>build-helper-maven-plugin</artifactId></plugin>"), module).contains("source merger"));
        String coupled = module.replace("<dependencies>",
                "<dependencies><dependency><groupId>ro.atf</groupId><artifactId>chess-core</artifactId></dependency>");
        check.same("Release tooling cannot acquire a dependency on the game", true, topology(reactor, consumer, coupled).contains("module dependencies"));
    }

    @Test void javaAndPowerShellLocateTheSameRelocatedCheckout(Checks check) throws Exception {
        Path root = fixture();
        Path scripts = root.resolve("release-tools/scripts");
        var layout = ReleaseWorkspace.locate(scripts);
        check.same("Java walks from the nested module to its owning checkout", root.toRealPath(), layout.root());
        var observed = powershell(". " + quote(helper()) + "; Get-ChessReleaseWorkspace -ScriptDirectory " + quote(scripts) + " | ConvertTo-Json -Compress");
        check.same("PowerShell resolves independently of the current directory", 0, observed.exit());
        var json = new ObjectMapper().readTree(observed.output());
        check.same("Both adapters identify the same project root", layout.root(), Path.of(json.path("ProjectRoot").asText()).toRealPath());
        check.same("Both adapters identify the relocated identity", layout.signing(), Path.of(json.path("Signing").asText()).normalize());
        Path orphan = Files.createDirectory(folder.resolve("outside"));
        check.rejects("Java rejects an unrelated folder", IOException.class, () -> ReleaseWorkspace.locate(orphan));
        var rejected = powershell(". " + quote(helper()) + "; Get-ChessReleaseWorkspace -ScriptDirectory " + quote(orphan));
        check.same("PowerShell rejects the same unrelated folder", true, rejected.exit() != 0 && rejected.output().contains("No Chess release workspace"));
    }

    @Test void signingRecoveryNeverCreatesOrOverwritesAMissingOrPartialIdentity(Checks check) throws Exception {
        Path root = fixture();
        Path scripts = root.resolve("release-tools/scripts");
        Path original = ProjectRoot.locate().resolve("release-tools/scripts/Initialize-J7Signing.ps1");
        Files.copy(original, scripts.resolve(original.getFileName()));
        Files.copy(helper(), scripts.resolve("ReleaseWorkspace.ps1"));
        Files.writeString(root.resolve("release/index.json"), "{\"currentVersion\":\"0.17.1\"}");
        Path reports = Files.createDirectories(root.resolve("release/0.17.1/reports"));
        Files.writeString(reports.resolve("qualification.json"), "{\"version\":\"0.17.1\",\"signingCertificateSha256\":\"" + "1".repeat(64) + "\"}");
        String command = ". " + quote(scripts.resolve("Initialize-J7Signing.ps1")) + " -ProjectIdentity -WorkRoot " + quote(folder.resolve("private logs"));
        var missing = powershell(command);
        check.same("Missing published identity requires explicit recovery", true,
                missing.exit() != 0 && missing.output().contains("published J7 signer is missing"));
        Path identity = root.resolve("release-tools/.atf-signing");
        check.same("Recovery did not silently create a new key directory", false, Files.exists(identity));
        Files.createDirectories(identity);
        Path certificate = identity.resolve("certificate.der");
        Files.writeString(certificate, "preserve partial record");
        var partial = powershell(command);
        check.same("Partial records fail before DPAPI or key generation", true,
                partial.exit() != 0 && partial.output().contains("Incomplete J7 signing identity"));
        check.same("Existing partial material is untouched", "preserve partial record", Files.readString(certificate));
        Files.createDirectories(root.resolve(".atf-signing"));
        var legacy = powershell(command);
        check.same("An old root identity cannot be silently mixed with the new location", true,
                legacy.exit() != 0 && legacy.output().contains("Legacy root signing record still exists"));
    }

    @Test void outputAndClasspathChecksFailBeforeCredentialsAreUsed(Checks check) throws Exception {
        Path root = fixture();
        var internal = powershell(". " + quote(helper()) + "; Assert-ChessExternalDirectory -Path " + quote(root.resolve("logs")) + " -ProjectRoot " + quote(root));
        check.same("Signing and download output cannot enter the source checkout", true,
                internal.exit() != 0 && internal.output().contains("outside the checkout"));
        Path existing = Files.createDirectory(folder.resolve("existing output"));
        var fresh = powershell(". " + quote(helper()) + "; Assert-ChessExternalDirectory -Path " + quote(existing) + " -ProjectRoot " + quote(root) + " -MustBeFresh");
        check.same("A download cannot overwrite an existing external directory", true, fresh.exit() != 0 && fresh.output().contains("fresh external"));
        var missing = powershell(". " + quote(helper()) + "; Get-ChessReleaseClasspath -ModuleRoot " + quote(root.resolve("release-tools")));
        check.same("Unbuilt tools cannot fall back to the consumer's stale classes", true,
                missing.exit() != 0 && missing.output().contains("Build the release-tools module"));
    }

    @Test void ragCoversTheModuleIdentityAndDeliveryScripts(Checks check) throws Exception {
        var paths = List.of("release-tools/pom.xml", "release-tools/scripts/Initialize-J7Signing.ps1",
                "release-tools/scripts/sync-published-release.ps1", "release-tools/.atf-signing/certificate.der");
        var result = ReleaseRag.inspect(ProjectRoot.locate(), paths);
        check.same("Every moved responsibility has an advisory owner", List.of(), result.needsReview());
        check.same("The module selects its component boundary checks", true,
                result.citations().stream().anyMatch(citation -> citation.check().contains("ReleaseModuleLayoutTest")));
        check.same("Identity changes retain the signer-continuity gate", true,
                result.citations().stream().anyMatch(citation -> citation.check().contains("signer continuity")));
    }

    @Test void gitPipesCompleteWithOneVirtualThreadCarrier(Checks check) throws Exception {
        Path output = Files.createTempFile(folder, "git-pipes-", ".txt");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        var process = new ProcessBuilder(java, "-Xmx64m", "-XX:ActiveProcessorCount=1",
                "-Djdk.virtualThreadScheduler.parallelism=1", "-Djdk.virtualThreadScheduler.maxPoolSize=1",
                "-cp", System.getProperty("java.class.path"), "ro.atf.chess.release.GitPipeProbe", ProjectRoot.locate().toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Git pipe probe did not terminate");
        }
        String observed = Files.readString(output);
        check.same("Independent pipe readers and writer complete under one carrier: " + observed, 0, process.exitValue());
        check.same("Large stdin and repeated binary batches are complete", true, observed.contains("PASS hash=262144 batch=16"));
    }

    private Path fixture() throws IOException {
        Path root = Files.createTempDirectory(folder, "checkout with spaces-");
        Files.createDirectories(root.resolve("release-tools/scripts"));
        Files.createDirectories(root.resolve("chess-core"));
        Files.createDirectories(root.resolve("release"));
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(root.resolve("release-tools/pom.xml"), "<project/>");
        Files.writeString(root.resolve("release/index.json"), "{}");
        return root;
    }

    private Path helper() { return ProjectRoot.locate().resolve("release-tools/scripts/ReleaseWorkspace.ps1"); }
    private String quote(Path value) { return "'" + value.toString().replace("'", "''") + "'"; }
    private record Execution(int exit, String output) { }

    private Execution powershell(String command) throws Exception {
        Path script = Files.createTempFile(folder, "probe-", ".ps1");
        Files.writeString(script, "$ErrorActionPreference = 'Stop'\n" + command);
        Path output = Files.createTempFile(folder, "output-", ".txt");
        var process = new ProcessBuilder(System.getProperty("chess.pwsh", "pwsh"), "-NoProfile", "-File", script.toString())
                .directory(folder.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Release workspace probe did not terminate");
        }
        return new Execution(process.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
    }

    private static List<String> topology(String reactor, String consumer, String module) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        var xpath = XPathFactory.newInstance().newXPath();
        var parent = builder.parse(new InputSource(new StringReader(reactor)));
        var tests = builder.parse(new InputSource(new StringReader(consumer)));
        var release = builder.parse(new InputSource(new StringReader(module)));
        var errors = new ArrayList<String>();
        var modules = new ArrayList<String>();
        for (var node : xpath.evaluateExpression("/project/modules/module", parent, XPathNodes.class)) modules.add(node.getTextContent().strip());
        if (!modules.equals(List.of("release-tools", "atf-tests"))) errors.add("reactor");
        String dependency = "/project/dependencies/dependency[groupId='ro.atf' and artifactId='chess-release-tools']";
        if (xpath.evaluateExpression(dependency, tests, XPathNodes.class).size() != 1
                || !List.of("", "compile").contains(xpath.evaluate(dependency + "/scope", tests).strip())
                || !xpath.evaluate(dependency + "/version", tests).strip().equals("${project.version}")) errors.add("dependency");
        if (xpath.evaluateExpression("//plugin[artifactId='build-helper-maven-plugin']", tests, XPathNodes.class).size() != 0) errors.add("source merger");
        var dependencies = xpath.evaluateExpression("/project/dependencies/dependency[not(scope='test')]", release, XPathNodes.class);
        if (dependencies.size() != 1 || !xpath.evaluate("/project/artifactId", release).equals("chess-release-tools")
                || !xpath.evaluate("/project/dependencies/dependency/artifactId", release).equals("snakeyaml")) errors.add("module dependencies");
        return errors;
    }
}
