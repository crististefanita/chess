package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Actual Native calls protect complete accepted routes, separately from the exploratory WDL grade. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class NativeContinuationRouteTest {
    @Test void retainsTraining056AfterTheKingReply(Checks check) throws Exception { verify(check, "training-056"); }
    @Test void retainsTraining091Continuation(Checks check) throws Exception { verify(check, "training-091"); }
    @Test void retainsTraining096Continuation(Checks check) throws Exception { verify(check, "training-096"); }

    private static void verify(Checks check, String id) throws Exception {
        com.fasterxml.jackson.databind.JsonNode input;
        try (var stream = NativeContinuationRouteTest.class.getResourceAsStream("/hard-lessons/late-route-guards.yaml")) {
            if (stream == null) throw new IllegalStateException("Missing retained-route origins");
            input = LearningGameAudit.mapper().readTree(stream);
        }
        if (!HardLeagueAudit.digest(input.toString().getBytes(StandardCharsets.UTF_8)).equals("ac7cf6a27f445469fc76fc6b15a6a9318b931525e6720268ffdbe987002ee33c"))
            throw new IllegalArgumentException("Changed retained-route fixture");
        check.same("The three independently confirmed regression origins are present", 3, input.path("cases").size());
        var rows = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>(); input.path("cases").forEach(rows::add);
        var row = rows.stream().filter(value -> value.path("id").asText().equals(id)).findFirst().orElseThrow();
        var quests = new java.util.ArrayList<QuestCatalog.Quest>(); quests.addAll(QuestCatalog.loadTactics()); quests.addAll(QuestCatalog.loadTraining());
        var quest = quests.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        check.same("The catalog origin has not been substituted", quest.fen(), row.path("initialFen").asText());
        var position = ChessPosition.fromFen(row.path("initialFen").asText());
        var game = ChessMatch.fromPosition(position, new MatchSetup(position.turn().other(), MatchSetup.Level.HARD, false));
        for (String move : row.path("prefix").asText().split(" ")) game = game.play(ChessPosition.Move.fromUci(move));
        int stage = row.path("stage").asInt();
        check.same("The actual history reaches the specified authored stage", quest.line().subList(0, stage * 2), LearningGameAudit.history(game));
        var actual = game.analyzeBot(MatchSetup.Level.HARD); HardQuestAudit.validate(game, actual);
        var assessment = quest.assess(actual.decision().move(), stage);
        check.same(id + " actual Native move " + actual.decision().move().uci() + " at H" + actual.decision().depth()
                + " with " + actual.decision().extraNodes() + " extra nodes preserves the accepted route", true,
                assessment.continues() || assessment.solved());
    }
}
