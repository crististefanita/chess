package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardLateContinuationTest {
    @Test void allTwentySixCapturedLateFailuresRetainTheirRealNativeReceipts(Checks check) throws Exception {
        var witnesses = HardLateContinuationWitnesses.load();
        for (var witness : witnesses) {
            var game = witness.game();
            var actual = HardQuestAudit.read(witness.input().path("baselineObserved"));
            HardQuestAudit.validate(game, actual);
            check.same(witness.id() + " teacher and Native use the same actual quiet-move clock",
                    game.searchQuietPlies(), Integer.parseInt(witness.phase(null, 19).path("nativeFen").asText().split(" ")[4]));
            check.same(witness.id() + " captured Native root is legal in its full actual history", true,
                    game.position().legalMoves().contains(actual.decision().move()));
            check.same(witness.id() + " stored observation is the captured compared root, not an injected solution",
                    witness.rejected(), actual.decision().move().uci());
        }
        check.same("Consumed late failures are complete; future engines need not repeat those choices", 26, witnesses.size());
    }

    @Test void catalogDepartureAndIndependentWdlLossAreDifferentContracts(Checks check) throws Exception {
        var witnesses = HardLateContinuationWitnesses.load();
        int large = 0;
        for (var witness : witnesses)
            if (witness.loss(witness.rejected(), 17) >= 150 && witness.loss(witness.rejected(), 19) >= 150) large++;
        check.same("Both teacher horizons confirm large WDL loss in sixteen captured decisions", 16, large);
        var sound = witnesses.stream().filter(value -> value.id().equals("tactic-084")).findFirst().orElseThrow();
        for (int depth : new int[]{17, 19})
            check.same("A different catalog move is not automatically a playing error at H" + depth, 0.0, sound.loss(sound.rejected(), depth));
        var bad = witnesses.stream().filter(value -> value.id().equals("training-100")).findFirst().orElseThrow();
        for (int depth : new int[]{17, 19})
            check.same("The actual training-100 choice has independently confirmed WDL loss at H" + depth, true, bad.loss(bad.rejected(), depth) >= 150);
    }

    @Test void foreignRunsStagesAndChangedWdlAreRejected(Checks check) throws Exception {
        var first = HardLateContinuationWitnesses.load().getFirst().input();
        String run = first.path("studyRun").asText();
        check.rejects("A phase cannot be relabelled as a different run", IllegalArgumentException.class,
                () -> HardLateContinuationWitnesses.read(first, "foreign-run"));
        var stage = first.deepCopy(); ((ObjectNode) stage).put("stage", 1);
        check.rejects("A later decision cannot borrow another stage", IllegalArgumentException.class,
                () -> HardLateContinuationWitnesses.read(stage, run));
        var score = first.deepCopy(); ((ArrayNode) score.path("phases").get(0).path("wdl")).set(0, LearningGameAudit.mapper().getNodeFactory().numberNode(123));
        check.rejects("Rewritten WDL cannot reuse the original exact UCI score", IllegalArgumentException.class,
                () -> HardLateContinuationWitnesses.read(score, run));
        var cap = first.deepCopy(); ((ObjectNode) cap.path("phases").get(0)).put("nodeSafetyCap", 0);
        check.rejects("An invalid safety cap cannot alter a declared completed-depth search", IllegalArgumentException.class,
                () -> HardLateContinuationWitnesses.read(cap, run));
        var corrected = HardLateContinuationWitnesses.load().stream().map(NativeFrontierWitnesses.Witness::input)
                .filter(value -> value.has("oracleInitialFen")).findFirst().orElseThrow();
        var missingClock = corrected.deepCopy(); ((ObjectNode) missingClock).remove("oracleInitialFen");
        check.rejects("A composed origin cannot silently borrow the catalog's nonzero earlier clock", IllegalArgumentException.class,
                () -> HardLateContinuationWitnesses.read(missingClock, run));
    }
}
