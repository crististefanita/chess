package ro.atf.chess;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Captured consumed training positions; no external engine or runtime move overrides. */
final class HardLateContinuationWitnesses {
    private static final String PIN = "b3e7c3cb65248feec3921295e8b5428ec6b14d6bc62434f434cea6cdf451bcac";
    static JsonNode input() throws Exception {
        try (var stream = HardLateContinuationWitnesses.class.getResourceAsStream("/hard-lessons/late-continuation-witnesses.yaml")) {
            require(stream != null, "Missing captured continuation witnesses");
            var tree = LearningGameAudit.mapper().readTree(stream);
            require(PIN.equals(HardLeagueAudit.digest(tree.toString().getBytes(StandardCharsets.UTF_8))), "Changed continuation fixture");
            require(tree.path("nativeExit").isInt() && tree.path("nativeExit").asInt() == 0, "Original teacher did not complete");
            require(tree.path("sourceCommit").asText().equals("cda04af9388707b3533d5cb487e5f2ccbfa8e5f5"), "Foreign Native source");
            require(tree.path("teacher").path("sha256").asText().equals("45bc8e4969147db9c2eb533810637994619bff0eacc81ccfd9854394901bcbd0"), "Foreign teacher binary");
            var header = tree.path("teacherHeader").toString();
            for (String marker : List.of("id name Stockfish 19", "uciok", "readyok", "> setoption name Hash value 32",
                    "> setoption name Threads value 1", "> setoption name UCI_LimitStrength value false", "> setoption name UCI_ShowWDL value true"))
                require(header.contains(marker), "Missing actual teacher setting: " + marker);
            return tree;
        }
    }
    static List<NativeFrontierWitnesses.Witness> load() throws Exception {
        var input = input();
        var witnesses = new ArrayList<NativeFrontierWitnesses.Witness>();
        var ids = new HashSet<String>();
        for (var row : input.path("cases")) {
            require(ids.add(row.path("id").asText()), "Duplicate continuation origin");
            witnesses.add(read(row, input.path("runId").asText()));
        }
        require(witnesses.size() == 26, "Incomplete continuation collection");
        return List.copyOf(witnesses);
    }
    static NativeFrontierWitnesses.Witness read(JsonNode row, String run) {
        require(run.equals(row.path("studyRun").asText()), "Foreign study run");
        var quests = new ArrayList<QuestCatalog.Quest>();
        quests.addAll(QuestCatalog.loadTactics()); quests.addAll(QuestCatalog.loadTraining()); quests.addAll(QuestCatalog.loadIntermezzo());
        var quest = quests.stream().filter(value -> value.id().equals(row.path("id").asText())).findFirst().orElseThrow();
        int stage = row.path("stage").asInt(-1);
        require(stage > 0 && stage < quest.playerMoves() && row.path("stage").isInt(), "Foreign late stage");
        require(quest.fen().equals(row.path("initialFen").asText())
                && quest.playerMove(stage).uci().equals(row.path("expected").asText()), "Foreign authored origin or reference");
        var prefix = new ArrayList<String>(); row.path("history").forEach(move -> prefix.add(move.asText()));
        require(prefix.equals(quest.line().subList(0, stage * 2).stream().map(ChessPosition.Move::uci).toList()), "Wrong actual prior catalog route");
        return NativeFrontierWitnesses.read(row);
    }
    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalArgumentException(reason);
    }
    private HardLateContinuationWitnesses() { }
}
