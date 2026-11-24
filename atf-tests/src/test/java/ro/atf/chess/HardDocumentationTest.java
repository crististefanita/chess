package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Language-neutral routes and identifiers; prose is reviewed separately in the EN/RO pair. */
@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class HardDocumentationTest {
    @Test void bothOwnerIndexesExposeTheirExperimentalHardGuide(Checks check) throws Exception {
        var root = ProjectRoot.locate();
        check.same("The board index links directly to its experimental search contract", true,
                Files.readString(root.resolve("docs/board/README.md")).contains("](hard-candidates.md)"));
        check.same("The feedback index links directly to the separate experimental backlog", true,
                Files.readString(root.resolve("docs/feedback/README.md")).contains("](hard.md)"));
        check.same("The experimental contract exists at the shared EN/RO path", true,
                Files.isRegularFile(root.resolve("docs/board/hard-candidates.md")));
        check.same("The experimental feedback exists at the shared EN/RO path", true,
                Files.isRegularFile(root.resolve("docs/feedback/hard.md")));
    }

    @Test void bothGuidesNameTheActualTechnicalRuntimeSelector(Checks check) throws Exception {
        var root = ProjectRoot.locate();
        String technicalSelector = "`" + HardMasterPolicy.RUNTIME.name() + "`";
        for (String path : new String[]{"docs/board/hard-candidates.md", "docs/feedback/hard.md"}) {
            check.same("The documented selector follows its executable policy in " + path, true,
                    Files.readString(root.resolve(path)).contains(technicalSelector));
        }
    }
}