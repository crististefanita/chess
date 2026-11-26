package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** The paired pins prevent a successful upload from silently drifting away from the deployment client. */
@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class PagesWorkflowComponentTest {
    @TempDir java.nio.file.Path output;
    @Test void pagesPublicationPinsBothArtifactClientsAndPreservesTheQualifiedHiddenFiles(Checks c) throws Exception {
        var file = ProjectRoot.locate().resolve(".github/workflows/chess-pages.yml");
        Map<?, ?> workflow = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
        var publish = (Map<?, ?>) ((Map<?, ?>) workflow.get("jobs")).get("publish");
        c.same("REST artifact validation has only read access to Actions", "read", ((Map<?, ?>) publish.get("permissions")).get("actions"));
        var steps = (List<?>) publish.get("steps");
        var upload = steps.stream().map(Map.class::cast).filter(row -> "Upload the verified site".equals(row.get("name"))).toList();
        var deploy = steps.stream().map(Map.class::cast).filter(row -> "deployment".equals(row.get("id"))).toList();
        c.same("Exactly one qualified upload feeds exactly one deployment", List.of(1, 1), List.of(upload.size(), deploy.size()));
        c.same("The upload retains the reviewed current artifact client", "actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9", upload.getFirst().get("uses"));
        c.same("Deployment retains its pinned direct API client", "actions/github-script@ed597411d8f924073f98dfc5c65a23a2325f34cd", deploy.getFirst().get("uses"));
        c.same("The upload exposes its exact artifact receipt", "pages-artifact", upload.getFirst().get("id"));
        c.same("Deployment receives that receipt rather than searching an artifact list", "${{ steps.pages-artifact.outputs.artifact_id }}",
                ((Map<?, ?>) deploy.getFirst().get("env")).get("CHESS_PAGES_ARTIFACT_ID"));
        var configuration = (Map<?, ?>) upload.getFirst().get("with");
        c.same("Only the independently verified extracted tree is uploaded", "${{ runner.temp }}/chess-site", configuration.get("path"));
        c.same("The new uploader explicitly preserves qualified dotfiles", true, configuration.get("include-hidden-files"));
        c.same("Site generation and test execution cannot be substituted during deployment", false,
                steps.stream().map(Map.class::cast).map(row -> String.valueOf(row.get("run")))
                        .anyMatch(command -> command.contains("Build-ChessWeb") || command.contains("mvn test")));
    }

    @Test void actualDeploymentScriptBindsTheUploadAndFailsClosedWithoutPublishingFalseSuccess(Checks c) throws Exception {
        var root = ProjectRoot.locate();
        Map<?, ?> workflow = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(root.resolve(".github/workflows/chess-pages.yml")));
        var steps = (List<?>) ((Map<?, ?>) ((Map<?, ?>) workflow.get("jobs")).get("publish")).get("steps");
        var deployment = steps.stream().map(Map.class::cast).filter(row -> "deployment".equals(row.get("id"))).findFirst().orElseThrow();
        var script = output.resolve("deployment.js");
        Files.writeString(script, (String) ((Map<?, ?>) deployment.get("with")).get("script"));
        var log = output.resolve("pages.tap");
        String run = UUID.randomUUID().toString();
        var builder = new ProcessBuilder("node", "--test", "--test-reporter=tap", root.resolve("web/tests/pages-deployment.test.mjs").toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("ATF_NODE_RUN_ID", run);
        builder.environment().put("CHESS_PAGES_SCRIPT", script.toString());
        var process = builder.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        c.same("The extracted real workflow script completes its component cases", true, finished);
        c.same("The original Node process succeeds", 0, process.exitValue());
        var rows = ChessWebStockfishTransportTest.read(Files.readString(log), run, Set.of(
                "Pages deploys the exact current upload with OIDC and waits for confirmed success",
                "Missing foreign or expired upload receipts cannot create a deployment",
                "Failure timeout and malformed deployment receipts never publish a success URL"));
        for (var row : rows) c.observed(row.get("case").asText() + " / " + row.get("name").asText(),
                row.get("expected").asText(), row.get("actual").asText(), row.get("passed").booleanValue());
    }
}
