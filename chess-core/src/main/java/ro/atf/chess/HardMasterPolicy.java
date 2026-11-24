package ro.atf.chess;

import java.util.Map;
import java.util.Set;

/** The application master is independent of the offline experiment selectors. */
record HardMasterPolicy(Engine engine, String id, HardCandidateSearch.Limits limits, int forcingReserve,
                        Certificate certificate, Evaluation evaluation, boolean rootPv, boolean pvs, boolean moveHints, boolean cutoffOrdering, QuietTactics quietTactics) {
    enum Engine { A, LEGACY }
    enum Evaluation { BASE, KING_PRESSURE, QUEEN, BOTH }
    enum QuietTactics {
        NONE(0), FORCING_PINS(7);
        private final int mode;
        QuietTactics(int mode) { this.mode = mode; }
        int mode() { return mode; }
    }
    record Certificate(int maxPlies, int nodes) {
        Certificate {
            if (!(maxPlies == 0 && nodes == 0) && (maxPlies < 1 || maxPlies > 7 || maxPlies % 2 == 0
                    || nodes < 1_000 || nodes > 500_000)) throw new IllegalArgumentException("Invalid mate certificate policy");
        }
        boolean enabled() { return nodes > 0; }
    }
    static final HardMasterPolicy CURRENT = read(ChessYaml.resource("hard-master.yaml"));
    static final Engine RUNTIME = runtime(ChessYaml.resource("hard-runtime.yaml"));

    static Engine runtime(Map<?, ?> root) {
        if (!root.keySet().equals(HardCollections.set("schema", "engine")) || ChessYaml.number(root, "schema") != 1) {
            throw new IllegalArgumentException("Invalid runtime engine selection");
        }
        return Engine.valueOf(ChessYaml.string(root, "engine"));
    }

    HardMasterPolicy(Engine engine, String id, HardCandidateSearch.Limits limits, int forcingReserve) {
        this(engine, id, limits, forcingReserve, new Certificate(0, 0));
    }
    HardMasterPolicy(Engine engine, String id, HardCandidateSearch.Limits limits, int forcingReserve, Certificate certificate) {
        this(engine, id, limits, forcingReserve, certificate, Evaluation.BASE, false, false, false);
    }
    HardMasterPolicy(Engine engine, String id, HardCandidateSearch.Limits limits, int forcingReserve, Certificate certificate,
                     Evaluation evaluation, boolean rootPv, boolean pvs, boolean moveHints) {
        this(engine, id, limits, forcingReserve, certificate, evaluation, rootPv, pvs, moveHints, false);
    }

    HardMasterPolicy(Engine engine, String id, HardCandidateSearch.Limits limits, int forcingReserve, Certificate certificate,
                     Evaluation evaluation, boolean rootPv, boolean pvs, boolean moveHints, boolean cutoffOrdering) {
        this(engine, id, limits, forcingReserve, certificate, evaluation, rootPv, pvs, moveHints, cutoffOrdering, QuietTactics.NONE);
    }

    private static final class NextHolder {
        static final HardMasterPolicy POLICY = read(ChessYaml.resource("hard-next.yaml"));
    }
    static HardMasterPolicy next() { return NextHolder.POLICY; }

    int evaluate(ChessPosition position) {
        int score = TacticalAnalysis.positionValue(position, position.turn());
        if (evaluation == Evaluation.QUEEN || evaluation == Evaluation.BOTH) score += QueenCounterplay.bonus(position);
        if (evaluation == Evaluation.KING_PRESSURE || evaluation == Evaluation.BOTH) score += PawnLeverPolicy.kingAttackPressure(position);
        int maximum = HardCandidateSearch.MATE - 129;
        return Math.max(-maximum, Math.min(maximum, score));
    }

    long maximumDecisionNodes() { return limits.nodeLimit() + certificate.nodes(); }

    HardMasterPolicy {
        java.util.Objects.requireNonNull(engine);
        java.util.Objects.requireNonNull(limits);
        java.util.Objects.requireNonNull(certificate);
        java.util.Objects.requireNonNull(evaluation);
        java.util.Objects.requireNonNull(quietTactics);
        if (id == null || !id.matches("hard-(?:a|legacy)-v[1-9][0-9]*")
                || !id.startsWith("hard-" + engine.name().toLowerCase(java.util.Locale.ROOT) + "-")) {
            throw new IllegalArgumentException("Master identity and engine disagree");
        }
        if (limits.nodeLimit() < 20_000 || limits.nodeLimit() > 2_000_000 || limits.maxDepth() < 3
                || limits.maxDepth() > 10 || limits.quiescencePlies() < 1 || forcingReserve < limits.quiescencePlies()
                || forcingReserve > 32 || limits.tableEntries() < 1 || limits.tableEntries() > 50_000) {
            throw new IllegalArgumentException("Unsupported master search policy");
        }
        if (certificate.enabled() && (engine != Engine.A || id.equals("hard-a-v1"))) {
            throw new IllegalArgumentException("A certified master needs its own versioned identity");
        }
        if ((rootPv || pvs || moveHints || cutoffOrdering || evaluation != Evaluation.BASE || quietTactics != QuietTactics.NONE)
                && (engine != Engine.A || id.equals("hard-a-v1") || id.equals("hard-a-v2"))) {
            throw new IllegalArgumentException("A changed search or evaluation needs a new A identity");
        }
        if (cutoffOrdering && id.equals("hard-a-v3")) throw new IllegalArgumentException("Cutoff ordering needs a new A identity");
        if (quietTactics != QuietTactics.NONE && id.matches("hard-a-v[1-5]")) {
            throw new IllegalArgumentException("Forcing pins need an A identity v6 or later");
        }
    }

    static HardMasterPolicy read(Map<?, ?> root) {
        int schema = ChessYaml.number(root, "schema");
        var keys = new java.util.HashSet<>(HardCollections.set("schema", "engine", "id", "max-plies", "total-nodes",
                "quiescence-plies", "forcing-reserve-plies", "table-entries", "evaluation", "ponder"));
        if (schema >= 2) {
            keys.remove("total-nodes");
            keys.addAll(HardCollections.set("core-nodes", "max-total-nodes", "mate-certificate"));
        }
        if (schema >= 3) keys.addAll(HardCollections.set("root-pv", "pvs", "move-hints"));
        if (schema >= 4) keys.add("cutoff-ordering");
        if (schema >= 5) keys.add("quiet-tactics");
        if (schema < 1 || schema > 5 || !root.keySet().equals(keys)) throw new IllegalArgumentException("Invalid master policy schema");
        var engine = Engine.valueOf(ChessYaml.string(root, "engine"));
        String id = ChessYaml.string(root, "id");
        int nodes = ChessYaml.number(root, schema >= 2 ? "core-nodes" : "total-nodes");
        int depth = ChessYaml.number(root, "max-plies");
        int q = ChessYaml.number(root, "quiescence-plies");
        int reserve = ChessYaml.number(root, "forcing-reserve-plies");
        int entries = ChessYaml.number(root, "table-entries");
        var evaluation = switch (ChessYaml.string(root, "evaluation")) {
            case "base" -> Evaluation.BASE;
            case "king-pressure" -> Evaluation.KING_PRESSURE;
            case "queen" -> Evaluation.QUEEN;
            case "both" -> Evaluation.BOTH;
            default -> throw new IllegalArgumentException("Unknown master evaluator");
        };
        if (schema < 3 && evaluation != Evaluation.BASE || !Boolean.FALSE.equals(root.get("ponder"))) {
            throw new IllegalArgumentException("Unsupported master search policy");
        }
        var certificate = new Certificate(0, 0);
        if (schema >= 2) {
            var row = ChessYaml.map(root, "mate-certificate");
            if (!row.keySet().equals(HardCollections.set("max-plies", "nodes"))) throw new IllegalArgumentException("Invalid certificate keys");
            certificate = new Certificate(ChessYaml.number(row, "max-plies"), ChessYaml.number(row, "nodes"));
        }
        var quietTactics = schema >= 5 ? switch (ChessYaml.string(root, "quiet-tactics")) {
            case "none" -> QuietTactics.NONE;
            case "forcing-pins" -> QuietTactics.FORCING_PINS;
            default -> throw new IllegalArgumentException("Unknown quiet tactical frontier");
        } : QuietTactics.NONE;
        boolean rootPv = schema >= 3 && flag(root, "root-pv"), pvs = schema >= 3 && flag(root, "pvs");
        var result = new HardMasterPolicy(engine, id, new HardCandidateSearch.Limits(depth, q, nodes, entries), reserve, certificate,
                evaluation, rootPv, pvs, schema >= 3 && flag(root, "move-hints"), schema >= 4 && flag(root, "cutoff-ordering"), quietTactics);
        if (schema >= 2 && ChessYaml.number(root, "max-total-nodes") != result.maximumDecisionNodes()) {
            throw new IllegalArgumentException("The maximum must include the full certificate reserve");
        }
        return result;
    }
    private static boolean flag(Map<?, ?> root, String key) {
        if (!(root.get(key) instanceof Boolean value)) throw new IllegalArgumentException("A boolean is required for " + key);
        return value;
    }
}
