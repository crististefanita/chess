package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** A known baseline or authored reference is not automatically the strongest move. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MaterialRegressionTest {
    private static List<NativeFrontierWitnesses.Witness> cases() throws Exception {
        com.fasterxml.jackson.databind.JsonNode tree;
        try (var stream = MaterialRegressionTest.class.getResourceAsStream("/hard-lessons/material-regression-witnesses.yaml")) {
            if (stream == null) throw new IllegalStateException("Missing independent material grades");
            tree = LearningGameAudit.mapper().readTree(stream);
        }
        if (!HardLeagueAudit.digest(tree.toString().getBytes(StandardCharsets.UTF_8)).equals("2d69718b422c941dee9f888a0a0a26d0bb7b0db30ed2d9e641220cb4c656c00a")
                || tree.path("nativeExit").asInt(-1) != 0 || !tree.path("runtimeLearningAdmitted").isBoolean()
                || tree.path("runtimeLearningAdmitted").asBoolean()) throw new IllegalArgumentException("Changed material witness or runtime scope");
        var result = new ArrayList<NativeFrontierWitnesses.Witness>();
        for (var row : tree.path("cases")) {
            if (!row.path("baselineRootRole").asText().equals("CAPTURED_BASELINE_NOT_INVARIABLY_CORRECT"))
                throw new IllegalArgumentException("Baseline cannot be silently labelled teacher");
            result.add(NativeFrontierWitnesses.read(row));
        }
        if (result.size() != 8) throw new IllegalArgumentException("Incomplete independent regression context");
        return List.copyOf(result);
    }
    private static NativeFrontierWitnesses.Witness witness(String id) throws Exception {
        return cases().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }
    @Test void threeExperimentalRouteLossesAreAlsoRealTeacherLosses(Checks check) throws Exception {
        for (String id : List.of("training-056", "training-091", "training-096")) {
            var w = witness(id);
            for (int depth : new int[]{17, 19}) {
                check.same(id + " baseline retains its teacher result at H" + depth, 0.0, w.loss(w.selected(), depth));
                check.same(id + " experimental move has independently confirmed WDL loss at H" + depth, true, w.loss(w.rejected(), depth) >= 150);
            }
        }
    }
    @Test void aHealthyAlternativeIsNotReportedAsAPlayingRegression(Checks check) throws Exception {
        for (String id : List.of("tactic-003", "training-040")) {
            var w = witness(id);
            for (int depth : new int[]{17, 19})
                check.same(id + " strict-route mismatch preserves teacher WDL at H" + depth, 0.0, w.loss(w.rejected(), depth));
        }
    }
    @Test void aLosingPawnCollectionCannotBecomeAGoldenAnswer(Checks check) throws Exception {
        var w = witness("training-060");
        for (int depth : new int[]{17, 19}) {
            check.same("Captured Kxg2 reference loses teacher WDL at H" + depth, 1000.0, w.loss(w.selected(), depth));
            check.same("Experimental e4 preserves teacher WDL at H" + depth, 0.0, w.loss(w.rejected(), depth));
        }
    }
    @Test void bothProtectedRookInvasionsRemainIndependentWinningControls(Checks check) throws Exception {
        for (String id : List.of("guard-training012", "guard-training012-mirror")) {
            var w = witness(id);
            for (int depth : new int[]{17, 19}) {
                check.same(id + " protected rook invasion retains teacher WDL at H" + depth, 0.0, w.loss(w.selected(), depth));
                check.same(id + " quiet pawn alternative loses teacher WDL at H" + depth, true, w.loss(w.rejected(), depth) >= 150);
            }
        }
    }
    @Test void originalLichessGameAndSolutionRejectTheGreedyPayoff(Checks check) throws Exception {
        com.fasterxml.jackson.databind.JsonNode api, oracle;
        try (var stream = MaterialRegressionTest.class.getResourceAsStream("/hard-lessons/lichess-0470w.json")) {
            if (stream == null) throw new IllegalStateException("Missing captured Lichess source");
            api = LearningGameAudit.mapper().readTree(stream);
        }
        check.same("The primary source response keeps its original integrity pin",
                "f8f19ef22dc313f4e562990b055117095e4bf4bf13b858efc84f144faf310b51",
                HardLeagueAudit.digest(api.toString().getBytes(StandardCharsets.UTF_8)));
        try (var stream = MaterialRegressionTest.class.getResourceAsStream("/hard-lessons/lichess-0470w-game-uci.json")) {
            if (stream == null) throw new IllegalStateException("Missing independent PGN replay");
            oracle = LearningGameAudit.mapper().readTree(stream);
        }
        var history = new ArrayList<ChessPosition.Move>(); oracle.path("uci").forEach(value -> history.add(ChessPosition.Move.fromUci(value.asText())));
        check.same("The independent PGN replay ends at the original puzzle ply", api.path("puzzle").path("initialPly").asInt() + 1, history.size());
        String fen = StudyFen.replay("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", history);
        check.same("Native and independent chess.js replay agree on the complete source FEN", oracle.path("fen").asText(), fen);
        var quest = QuestCatalog.loadTraining().stream().filter(value -> value.id().equals("training-060")).findFirst().orElseThrow();
        check.same("The retained curriculum identity still refers to the actual source origin", quest.fen(), fen);
        var independent = witness("training-060");
        check.same("The original source continues with the independently winning pawn race",
                independent.rejected(), api.path("puzzle").path("solution").get(2).asText());
        check.same("The corrected public payoff now uses the independently winning continuation",
                independent.rejected(), quest.playerMove(1).uci());
        var position = quest.position();
        for (var move : api.path("puzzle").path("solution")) position = position.play(ChessPosition.Move.fromUci(move.asText()));
        check.same("The actual source reaches the protected promotion threat", ChessPosition.Kind.PAWN, position.pieceAt(BoardSquare.parse("e2")).kind());
    }
}
