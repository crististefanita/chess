package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
class HardConfirmationStudyTest {
    private static Path out() { return Path.of(System.getProperty("chess.confirmation.out")); }
    private static HardConfirmationLeague.Configuration config() {
        return HardConfirmationLeague.configuration(System.getProperty("chess.confirmation.reference", "BASE_A"));
    }
    @Test @EnabledIfSystemProperty(named="chess.confirmation.phase", matches="freeze")
    void freezesOneHundredNewStartsBeforeAnyEngineGame(Checks check) throws Exception {
        String exclusions = System.getProperty("chess.confirmation.exclusions", "");
        var paths = exclusions.isBlank() ? java.util.List.<Path>of()
                : Arrays.stream(exclusions.split(";")).map(Path::of).toList();
        HardConfirmationLeague.freeze(out(), config(), paths);
        var starts = HardConfirmationLeague.load(out(), config());
        check.same("Ten opening families provide ten distinct, predeclared starts each", 100, starts.size());
        check.same("The same inputs preserve deterministic opening choices", starts, HardConfirmationLeague.load(out(), config()));
    }
    @Test @EnabledIfSystemProperty(named="chess.confirmation.phase", matches="run")
    void measuresTheIncrementalCandidateAgainstTheEqualBudgetMaster(Checks check) throws Exception {
        var config = config();
        HardConfirmationLeague.run(out(), config, Integer.getInteger("chess.confirmation.workers", 4));
        check.same("All 200 games reconcile, including capped and unavailable dispositions", 201,
                java.nio.file.Files.readAllLines(out().resolve("games.tsv")).size());
        check.same("The main reference has the same total allowance; native extra costs are a separate diagnostic", true,
                config.reference().equals("LEGACY") || config.candidate().maximumDecisionNodes() == config.baseline().maximumDecisionNodes());
    }
    @Test @EnabledIfSystemProperty(named="chess.confirmation.phase", matches="replay")
    void independentlyReconstructsEveryGamePvCostAndEloSummary(Checks check) throws Exception {
        HardConfirmationLeague.verify(out(), config());
        check.same("The complete paired roster is present", 201, java.nio.file.Files.readAllLines(out().resolve("games.tsv")).size());
        check.same("The summary is derived from raw complete games, not labels", 2,
                java.nio.file.Files.readAllLines(out().resolve("summary.tsv")).size());
    }
}