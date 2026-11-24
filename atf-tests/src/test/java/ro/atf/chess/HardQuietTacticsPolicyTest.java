package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class HardQuietTacticsPolicyTest {
    private static Map<Object, Object> schemaFour() {
        var data = new LinkedHashMap<Object, Object>();
        data.put("schema", 4); data.put("engine", "A"); data.put("id", "hard-a-v5");
        data.put("max-plies", 3); data.put("core-nodes", 20_000); data.put("max-total-nodes", 20_000);
        data.put("quiescence-plies", 8); data.put("forcing-reserve-plies", 8); data.put("table-entries", 128);
        data.put("evaluation", "base"); data.put("ponder", false);
        data.put("mate-certificate", Map.of("max-plies", 0, "nodes", 0));
        data.put("root-pv", true); data.put("pvs", true); data.put("move-hints", true); data.put("cutoff-ordering", true);
        return data;
    }
    private static Map<Object, Object> schemaFive() {
        var data = schemaFour(); data.put("schema", 5); data.put("id", "hard-a-v6");
        data.put("quiet-tactics", "forcing-pins"); return data;
    }

    @Test void everyHistoricalSchemaKeepsItsOriginalDisabledFrontier(Checks check) {
        var versions = new ArrayList<Map<Object, Object>>();
        var four = schemaFour(); versions.add(four);
        var three = new LinkedHashMap<>(four); three.put("schema", 3); three.put("id", "hard-a-v3"); three.remove("cutoff-ordering"); versions.add(three);
        var two = new LinkedHashMap<>(three); two.put("schema", 2); two.put("id", "hard-a-v2");
        two.remove("root-pv"); two.remove("pvs"); two.remove("move-hints"); versions.add(two);
        var one = new LinkedHashMap<>(two); one.put("schema", 1); one.put("id", "hard-a-v1");
        one.put("total-nodes", one.remove("core-nodes")); one.remove("max-total-nodes"); one.remove("mate-certificate"); versions.add(one);
        for (var data : versions) {
            var policy = HardMasterPolicy.read(data);
            check.same("Schema " + data.get("schema") + " does not silently enable new tactical search", HardMasterPolicy.QuietTactics.NONE, policy.quietTactics());
            check.same("Historical NONE maps to the same disabled search mode", 0, policy.quietTactics().mode());
            check.same("The historical core cap is unchanged", 20_000L, policy.limits().nodeLimit());
        }
    }

    @Test void historicalConstructorsRemainSourceAndBehaviorCompatible(Checks check) {
        var base = HardMasterPolicy.read(schemaFour());
        var old = List.of(
                new HardMasterPolicy(HardMasterPolicy.Engine.A, "hard-a-v1", base.limits(), base.forcingReserve()),
                new HardMasterPolicy(HardMasterPolicy.Engine.A, "hard-a-v2", base.limits(), base.forcingReserve(), base.certificate()),
                new HardMasterPolicy(HardMasterPolicy.Engine.A, "hard-a-v5", base.limits(), base.forcingReserve(), base.certificate(), base.evaluation(), true, true, true),
                new HardMasterPolicy(HardMasterPolicy.Engine.A, "hard-a-v5", base.limits(), base.forcingReserve(), base.certificate(), base.evaluation(), true, true, true, true));
        for (var policy : old) check.same("An old constructor cannot acquire the new frontier", HardMasterPolicy.QuietTactics.NONE, policy.quietTactics());
    }

    @Test void theNamedSchemaHasAnExplicitModeAndNoHiddenExtraAllowance(Checks check) {
        var policy = HardMasterPolicy.read(schemaFive());
        check.same("The named option maps to the qualified algorithmic mode", HardMasterPolicy.QuietTactics.FORCING_PINS, policy.quietTactics());
        check.same("The implementation mode is explicit", 7, policy.quietTactics().mode());
        check.same("The named option cannot add uncharged nodes", 20_000L, policy.maximumDecisionNodes());
        var none = schemaFive(); none.put("quiet-tactics", "none");
        check.same("The new schema can explicitly select its baseline", HardMasterPolicy.QuietTactics.NONE, HardMasterPolicy.read(none).quietTactics());
    }

    @Test void wrongNamesKeysSchemasAndReusedIdentitiesAreRejected(Checks check) {
        for (Object value : List.of("unknown", "FORCING_PINS", "7", true)) {
            var data = schemaFive(); data.put("quiet-tactics", value);
            check.rejects("Unsupported frontier value is not silently coerced: " + value, IllegalArgumentException.class, () -> HardMasterPolicy.read(data));
        }
        var missing = schemaFive(); missing.remove("quiet-tactics");
        check.rejects("Schema five cannot omit its declared frontier", IllegalArgumentException.class, () -> HardMasterPolicy.read(missing));
        var old = schemaFive(); old.put("schema", 4);
        check.rejects("The old schema cannot silently acquire the new key", IllegalArgumentException.class, () -> HardMasterPolicy.read(old));
        var extra = schemaFive(); extra.put("quiet-mode", 7);
        check.rejects("Unknown keys cannot change behavior outside the schema", IllegalArgumentException.class, () -> HardMasterPolicy.read(extra));
        var future = schemaFive(); future.put("schema", 6);
        check.rejects("An unknown future schema is rejected", IllegalArgumentException.class, () -> HardMasterPolicy.read(future));
        var reused = schemaFive(); reused.put("id", "hard-a-v5");
        check.rejects("New behavior cannot reuse the previous candidate's identity", IllegalArgumentException.class, () -> HardMasterPolicy.read(reused));
    }

}
