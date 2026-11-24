package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardMasterEvidenceTest {
    @Test void anUnfinishedForcingFrontierHasAnAuditableReceiptButNoPlayableDecision(Checks check) {
        var game = ChessMatch.fromPosition(ChessPosition.fromFen("6k1/8/3p4/4r3/4Q3/8/8/6K1 w - - 0 1"));
        var policy = new HardMasterPolicy(HardMasterPolicy.Engine.A, "hard-a-v3",
                new HardCandidateSearch.Limits(3, 1, 20_000, 1_024), 1);
        try (var scope = new HardSearchExperiment(HardSearchExperiment.Bonuses.NONE, p -> 0, 20_000, 1_024)) {
            var report = HardMaster.search(game, policy, policy::evaluate, false);
            check.same("The incomplete receipt keeps its stop reason", HardCandidateSearch.Stop.QUIESCENCE_LIMIT, report.result().stop());
            check.same("No numeric result is invented", null, report.result().score());
            check.same("No executable principal variation is invented", List.of(), report.result().line());
            check.same("The entire unsuccessful attempt remains accounted", scope.nodes, report.result().nodes());
            check.same("The probe actually performed work", true, scope.nodes > 0);
        }
        check.rejects("A report without a completed horizon is never played by the application", IllegalStateException.class,
                () -> HardMaster.decide(game, policy, false));
    }
}
