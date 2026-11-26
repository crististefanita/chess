package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** The paired pins prevent a successful upload from silently drifting away from the deployment client. */
@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class PagesWorkflowComponentTest {
    @Test void pagesPublicationPinsBothArtifactClientsAndPreservesTheQualifiedHiddenFiles(Checks c) throws Exception {
        var file = ProjectRoot.locate().resolve(".github/workflows/chess-pages.yml");
        Map<?, ?> workflow = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
        var publish = (Map<?, ?>) ((Map<?, ?>) workflow.get("jobs")).get("publish");
        var steps = (List<?>) publish.get("steps");
        var upload = steps.stream().map(Map.class::cast).filter(row -> "Upload the verified site".equals(row.get("name"))).toList();
        var deploy = steps.stream().map(Map.class::cast).filter(row -> "deployment".equals(row.get("id"))).toList();
        c.same("Exactly one qualified upload feeds exactly one deployment", List.of(1, 1), List.of(upload.size(), deploy.size()));
        c.same("The upload retains the reviewed current artifact client", "actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9", upload.getFirst().get("uses"));
        c.same("Deployment retains its paired reviewed client", "actions/deploy-pages@368f82528645a54fb793d4d04e342629a3f51346", deploy.getFirst().get("uses"));
        var configuration = (Map<?, ?>) upload.getFirst().get("with");
        c.same("Only the independently verified extracted tree is uploaded", "${{ runner.temp }}/chess-site", configuration.get("path"));
        c.same("The new uploader explicitly preserves qualified dotfiles", true, configuration.get("include-hidden-files"));
        c.same("Site generation and test execution cannot be substituted during deployment", false,
                steps.stream().map(Map.class::cast).map(row -> String.valueOf(row.get("run")))
                        .anyMatch(command -> command.contains("Build-ChessWeb") || command.contains("mvn test")));
    }
}
