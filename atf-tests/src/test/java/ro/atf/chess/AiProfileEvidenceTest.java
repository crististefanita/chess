package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AiProfileEvidenceTest {
    private static final String RESOURCE = "/hard-lessons/profile-receipt-counterexamples.yaml";
    private static com.fasterxml.jackson.databind.JsonNode cases() throws Exception {
        try (var input = AiProfileEvidenceTest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing captured Native receipt");
            var bytes = input.readAllBytes();
            var tree = LearningGameAudit.mapper().readTree(bytes);
            if (!HardLeagueAudit.digest(tree.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .equals("3ad605607fda0fa2a89d313cf94e052e829cc270c8eb3c4e96ff00612e14601f"))
                throw new IllegalStateException("Changed captured Native observations");
            return tree.path("cases");
        }
    }
    private static ChessMatch context(com.fasterxml.jackson.databind.JsonNode row) {
        var side = ChessPosition.Side.valueOf(row.path("nativeSide").asText());
        var game = ChessMatch.start(new MatchSetup(side.other(), MatchSetup.Level.HARD, false), row.path("choice").asLong());
        for (String move : row.path("prefix").asText().split(" ")) game = game.play(ChessPosition.Move.fromUci(move));
        return game;
    }

    @Test void capturedHistoryChoicesAreValidWithoutBecomingWinningPvProofs(Checks check) throws Exception {
        for (var row : cases()) {
            var game = context(row);
            var observed = HardQuestAudit.read(row.path("observation"));
            AiProfileEvidence.validateNative(game, MatchSetup.Level.HARD, observed);
            check.same("Captured history-aware choice is reconstructed: " + row.path("caseId").asText(),
                    observed.decision().move(), AiProfileEvidence.selected(game, MatchSetup.Level.HARD, observed.search().result()));
            check.rejects("The old strict root/PV certificate still rejects this observation: " + row.path("caseId").asText(),
                    IllegalArgumentException.class, () -> HardQuestAudit.validate(game, observed));
        }
    }

    @Test void aLegalMoveAloneDoesNotAuthorizeAChangedDecision(Checks check) throws Exception {
        var row = cases().get(0);
        var game = context(row);
        var observed = HardQuestAudit.read(row.path("observation"));
        var foreign = game.position().legalMoves().stream().filter(move -> !move.equals(observed.decision().move())).findFirst().orElseThrow();
        var changed = new ChessMatch.BotAnalysis(new ChessMatch.BotDecision(foreign, observed.decision().depth(), observed.decision().extraNodes()), observed.search());
        check.rejects("A legal substituted root cannot borrow the actual ranking and history", IllegalArgumentException.class,
                () -> AiProfileEvidence.validateNative(game, MatchSetup.Level.HARD, changed));
        var missing = new ChessMatch.BotAnalysis(observed.decision(), new TacticalAnalysis.SearchReport(new TacticalAnalysis.Result(List.of()), observed.search().depth(), observed.search().extraNodes()));
        check.rejects("An empty ranking cannot pass as an actual Native observation", IllegalArgumentException.class,
                () -> AiProfileEvidence.validateNative(game, MatchSetup.Level.HARD, missing));
        var miscounted = new ChessMatch.BotAnalysis(new ChessMatch.BotDecision(observed.decision().move(), observed.decision().depth(), observed.decision().extraNodes() - 1), observed.search());
        check.rejects("The receipt cannot alter reported work", IllegalArgumentException.class,
                () -> AiProfileEvidence.validateNative(game, MatchSetup.Level.HARD, miscounted));
    }

    @Test void aQuietKingMoveCanUnlockTheAttackingRook(Checks check) throws Exception {
        var lesson = ChessYaml.resource("hard-lessons/quiet-unpin-plan.yaml");
        var witness = NativeFrontierWitnesses.load().stream().filter(value -> value.id().equals(lesson.get("referenceWitness"))).findFirst().orElseThrow();
        var plan = (List<?>) lesson.get("plan");
        String prefix = " pv " + String.join(" ", plan.stream().map(String.class::cast).toList());
        boolean captured = false;
        for (var line : witness.phase(null, ChessYaml.number(lesson, "referenceDepth")).path("raw"))
            if (line.asText().startsWith("info depth 19 ") && line.asText().contains(prefix)) captured = true;
        check.same("The didactic plan is present in the independently pinned H19 reference", true, captured);
        var pinned = ChessPosition.fromFen((String) lesson.get("pinnedFen"));
        var rook = ChessPosition.Move.fromUci((String) lesson.get("rookCheck"));
        check.same("The queen pins Rg3 to Kh2; Re3 is illegal before the quiet move", false, pinned.legalMoves().contains(rook));
        var free = pinned.play(ChessPosition.Move.fromUci((String) lesson.get("quietUnpin"))).play(ChessPosition.Move.fromUci((String) lesson.get("storedDefense")));
        check.same("After Kh3 and the stored queen reply the rook can join with check", true, free.play(rook).isCheck());
        var counterexample = ChessYaml.map(lesson, "counterexample");
        var danger = ChessPosition.fromFen((String) counterexample.get("fen"));
        check.same("Synthetic bishop counterexample forbids Kh3; this is not an unconditional king rule", false,
                danger.legalMoves().contains(ChessPosition.Move.fromUci("h2h3")));
    }

    @Test void changedEvidenceCannotBorrowAnOriginalIntegrityPin(Checks check, @TempDir java.nio.file.Path folder) throws Exception {
        var file = folder.resolve("native.jsonl");
        java.nio.file.Files.writeString(file, "original native observation");
        String pin = HardLeagueAudit.digest(java.nio.file.Files.readAllBytes(file));
        AiProfileEvidence.bound(file, pin);
        check.same("Original bytes remain bound", true, java.nio.file.Files.isRegularFile(file));
        java.nio.file.Files.writeString(file, "substituted observation");
        check.rejects("Changed evidence cannot reuse the original pin", IllegalArgumentException.class, () -> AiProfileEvidence.bound(file, pin));
        check.rejects("A missing integrity pin cannot become a successful comparison", IllegalArgumentException.class, () -> AiProfileEvidence.bound(file, ""));
    }
}
