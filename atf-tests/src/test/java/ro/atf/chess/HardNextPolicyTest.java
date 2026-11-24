package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardNextPolicyTest {
    @Test void theNextAlgorithmHasAnExplicitVersionedPolicyAndCompleteNodeAllowance(Checks check) {
        var next = HardMasterPolicy.next();
        check.same("A changed algorithm cannot reuse the baseline identity", "hard-a-v6", next.id());
        check.same("Core, depth and forcing horizon are explicit", new HardCandidateSearch.Limits(8, 32, 1_000_000, 22_000), next.limits());
        check.same("Proof visits are included in the maximum", 1_100_000L, next.maximumDecisionNodes());
        check.same("The selected evaluator is explicit", HardMasterPolicy.Evaluation.KING_PRESSURE, next.evaluation());
        check.same("The candidate explicitly selects the named tactical frontier", HardMasterPolicy.QuietTactics.FORCING_PINS, next.quietTactics());
        check.same("The named frontier maps to the tested algorithmic mode", 7, next.quietTactics().mode());
        check.same("The configured candidate uses its explicit frontier schema", 5, ChessYaml.number(ChessYaml.resource("hard-next.yaml"), "schema"));
        check.same("All enabled search mechanisms are declared", true, next.rootPv() && next.pvs() && next.moveHints() && next.cutoffOrdering());
        check.same("A candidate policy does not activate the public game", HardMasterPolicy.Engine.LEGACY, HardMasterPolicy.RUNTIME);
    }

    @Test void policyParsingRejectsSilentSearchChangesAndNonBooleanFlags(Checks check) {
        var data = new HashMap<Object, Object>(ChessYaml.resource("hard-next.yaml"));
        data.put("pvs", "true");
        check.rejects("A quoted flag cannot silently change search semantics", IllegalArgumentException.class, () -> HardMasterPolicy.read(data));
        data.put("pvs", true); data.put("id", "hard-a-v2");
        check.rejects("New algorithms need a new identity", IllegalArgumentException.class, () -> HardMasterPolicy.read(data));
        data.put("id", "hard-a-v6"); data.put("max-total-nodes", 1_000_000);
        check.rejects("The certificate is never free or hidden", IllegalArgumentException.class, () -> HardMasterPolicy.read(data));
        data.put("max-total-nodes", 1_100_000); data.put("evaluation", "unknown-model");
        check.rejects("A missing model cannot fall back to a different evaluator", IllegalArgumentException.class, () -> HardMasterPolicy.read(data));
    }

    @Test void experimentalDeltasCannotLeakIntoTheProductionEvaluator(Checks check) {
        var next = HardMasterPolicy.next();
        var board = QuestCatalog.loadTraining().getFirst().position();
        int before = next.evaluate(board);
        try (var ignored = new HardSearchExperiment(HardSearchExperiment.Bonuses.BOTH, p -> 90_000, 1_100_000, 22_000)) {
            check.same("The selected candidate evaluator is independent of the experimental scope", before, next.evaluate(board));
        }
        check.same("Static evaluation keeps distance from the mate score range", true, Math.abs((long) before) < HardCandidateSearch.MATE - 128);
    }
}
