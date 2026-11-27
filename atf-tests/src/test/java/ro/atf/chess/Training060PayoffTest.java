package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;

/** Source curation must preserve the learning goal, not merely the historical key. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class Training060PayoffTest {
    private static final String ID = "training-060";
    private static QuestCatalog.Quest quest() {
        return QuestCatalog.loadTraining().stream().filter(q -> q.id().equals(ID)).findFirst().orElseThrow();
    }
    private static List<String> moves(QuestCatalog.Quest q) { return q.line().stream().map(Move::uci).toList(); }
    private static JsonNode fixture() throws Exception {
        try (var input = Training060PayoffTest.class.getResourceAsStream("/hard-lessons/training-060-payoff-proof.json")) {
            if (input == null) throw new IOException("Missing payoff teacher proof");
            return readProof(input.readAllBytes());
        }
    }

    private static JsonNode readProof(byte[] bytes) throws IOException {
        var root = LearningGameAudit.mapper().readTree(bytes);
        if (!HardLeagueAudit.digest(root.toString().getBytes(StandardCharsets.UTF_8))
                .equals("387ae9d963c736f203b37a647be9db59ae38ff9dd2dda359061bd309b1590de8")) {
            throw new IllegalArgumentException("Changed original payoff proof");
        }
        return root;
    }

    @Test void proofPinSurvivesLfAndCrlfButRejectsChangedMeaning(Checks check) throws Exception {
        String encoded;
        try (var input = Training060PayoffTest.class.getResourceAsStream("/hard-lessons/training-060-payoff-proof.json")) {
            encoded = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        check.same("Native LF and Windows CRLF preserve the same pinned teacher evidence",
                readProof(encoded.getBytes(StandardCharsets.UTF_8)),
                readProof(encoded.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8)));
        var changed = (ObjectNode) fixture();
        changed.put("runId", "foreign-run");
        check.rejects("Whitespace normalization cannot admit changed teacher facts", IllegalArgumentException.class,
                () -> readProof(LearningGameAudit.mapper().writeValueAsBytes(changed)));
    }

    @Test void preservesTheSourceIdFenKeyRankAndOriginalSevenPlies(Checks check) throws Exception {
        var q = quest();
        JsonNode source;
        try (var input = Training060PayoffTest.class.getResourceAsStream("/hard-lessons/lichess-0470w.json")) {
            source = LearningGameAudit.mapper().readTree(input);
        }
        var prefix = new ArrayList<String>();
        source.path("puzzle").path("solution").forEach(move -> prefix.add(move.asText()));
        check.same("The source identity and unchanged key remain available to existing progress",
                List.of(ID, "8/6p1/4p3/2K1p3/1P5p/7P/4kPP1/8 b - - 2 36", "e2f2", 20),
                List.of(q.id(), q.fen(), q.solution().uci(), QuestCatalog.loadTraining().stream().map(QuestCatalog.Quest::id).toList().indexOf(ID)));
        check.same("The closed payoff is a legal 27-ply continuation", 27, q.line().size());
        check.same("All seven original Lichess moves precede the newly verified extension", prefix, moves(q).subList(0, 7));
        check.same("The honest extension has 27 plies and teaches promotion", List.of(27, QuestCatalog.Lesson.PROMOTION),
                List.of(q.line().size(), q.lesson()));
        check.same("The public training collection still has 114 stable IDs", 114, QuestCatalog.loadTraining().size());
    }

    @Test void bothPromotionsMustBeShownBeforeTheClosedMaterialGoal(Checks check) {
        var q = quest();
        var replay = QuestPayoff.forQuest(q);
        check.same("The old greedy Kxg2 is legal but cannot complete the corrected race", false,
                q.assess(Move.fromUci("f2g2"), 1).solved());
        check.same("The authentic second solver move advances the pawn and retains the stage", List.of(true, false),
                List.of(q.assess(Move.fromUci("e5e4"), 1).continues(), q.assess(Move.fromUci("e5e4"), 1).solved()));
        check.same("A provisional queen advantage cannot end the lesson before the counterpromotion",
                List.of(100, -700, 100), List.of(replay.at(7).materialChange(), replay.at(8).materialChange(), replay.at(9).materialChange()));
        check.same("Both promotions belong to the recorded legal replay", List.of("b7b8q", "e2e1q"), moves(q).subList(7, 9));
        check.same("Black's promotion schedules the remaining defense and never awards a premature solve", List.of(true, false),
                List.of(q.assess(Move.fromUci("e2e1q"), 4).continues(), q.assess(Move.fromUci("e2e1q"), 4).solved()));
    }

    @Test void theFinalTwoPawnGainSurvivesEveryImmediateLegalDefense(Checks check) {
        var q = quest();
        var finish = q.solvedPosition();
        var side = q.position().turn();
        int original = QuestPayoff.balance(q.position(), side);
        check.same("The existing closed +200 objective is preserved", 200, q.materialChange());
        check.same("The final board remains a playable queen ending, not a mate/stalemate shortcut", true,
                !finish.legalMoves().isEmpty() && !finish.isCheckmate());
        for (var reply : finish.legalMoves()) {
            var after = finish.play(reply);
            check.same("The material survives legal reply " + reply.uci(), true,
                    QuestPayoff.balance(after, side) - original >= 200 && !after.legalMoves().isEmpty());
        }
        check.same("Only the actual final solver stage completes", true,
                q.assess(q.finishingMove(), q.playerMoves() - 1).solved());
    }

    @Test void theHintAndPendingDefenseStayOnTheExactPlayableStage(Checks check) {
        var q = quest();
        check.same("The initial hint names the correct black king", Kind.KING, q.guidance().piece().kind());
        for (int stage = 0; stage < q.playerMoves(); stage++) {
            var position = q.positionAt(stage);
            var move = q.playerMove(stage);
            check.same("The stage " + stage + " hint is a legal move by the current solver", true,
                    position.legalMoves().contains(move) && position.pieceAt(move.from()).side() == position.turn());
            var attempt = q.assess(move, stage);
            if (stage + 1 < q.playerMoves()) {
                check.same("A correct intermediate hint cannot unlock Next at stage " + stage, List.of(true, false),
                        List.of(attempt.continues(), attempt.solved()));
                var pending = QuestStep.restore(stage * 2 + 1, q);
                check.same("Restart before defense shows the exact played hint at stage " + stage, true,
                        pending.defensePending() && pending.position(q).sameForRepetition(position.play(move)));
                check.same("The queued defense reaches the same next board once at stage " + stage, true,
                        pending.position(q).play(q.defenseAfter(stage)).sameForRepetition(attempt.position()));
            }
        }
    }

    @Test void existingSolvedIdsAwardsAndUnsolvedPrefixSurviveTheCorrection(Checks check) {
        var q = quest();
        var order = QuestCatalog.loadTraining().stream().map(QuestCatalog.Quest::id).toList();
        var disk = new MemoryPreferences();
        var store = new QuestStore(disk);
        var progress = store.read(order).select(ID);
        var award = store.readRewards().complete(progress);
        check.same("Save the old edition's awarded ID and greedy finish", true,
                store.saveFinish(progress.solveCurrent(), award.rewards(), "1:f2g2"));
        var reopened = new QuestStore(disk);
        var restored = reopened.read(order);
        check.same("The corrected line preserves the saved solved ID and award once", List.of(true, Set.of(ID), 0),
                List.of(restored.currentSolved(), restored.solvedIds(), reopened.readRewards().complete(restored).amount()));
        check.same("The obsolete losing finish cannot replace the new explanation", moves(q),
                moves(q.restoreFinish(reopened.finish(ID))));
        check.same("An old unsolved second-stage prefix is still the same board", true,
                QuestStep.restore(2, q).position(q).sameForRepetition(q.position().play(Move.fromUci("e2f2")).play(Move.fromUci("b4b5"))));
        var waiting = QuestProgress.restore(ID, Set.of(), order);
        check.same("The source key cannot unlock Next in an unsolved imported attempt", false,
                waiting.canAdvance(order.indexOf(ID), order.size()));
    }

    @Test void bothTeacherHorizonsRetainEveryActualChoiceAgainstFreeReplies(Checks check) throws Exception {
        var proof = fixture();
        validateProof(proof, quest());
        check.same("The final frozen audit contains every ply at H17 and H19", List.of(54, 58, true),
                List.of(proof.path("completion").path("grades").size(), proof.path("phases").size(),
                        proof.path("completion").path("allGradesWithin50").asBoolean()));
        check.same("Every actual choice preserves the free teacher WDL at the same horizon", true,
                java.util.stream.StreamSupport.stream(proof.path("completion").path("grades").spliterator(), false)
                        .allMatch(row -> row.path("loss").asDouble() == 0));
    }

    @Test void proofAdmissionRejectsForeignRunsDepthsMovesAndMissingReplicas(Checks check) throws Exception {
        var original = fixture();
        var wrongRun = original.deepCopy();
        ((ObjectNode) wrongRun.path("phases").get(0)).put("runId", "foreign-run");
        var wrongDepth = original.deepCopy();
        ((ObjectNode) wrongDepth.path("phases").get(0)).put("depth", 16);
        var wrongPosition = original.deepCopy();
        ((ObjectNode) wrongPosition.path("phases").get(0)).put("positionCommand", "position startpos");
        var wrongMove = original.deepCopy();
        ((ObjectNode) wrongMove.path("completion").path("grades").get(0)).put("move", "f2g2");
        var omitted = original.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) omitted.path("completion").path("grades")).remove(0);
        var inventedWdl = original.deepCopy();
        ((ObjectNode) inventedWdl.path("phases").get(0)).put("expectedPerMille", 500);
        var wrongClock = original.deepCopy();
        ((ObjectNode) wrongClock.path("phases").get(0)).put("fen", quest().fen().replace("2 36", "0 1"));
        int index = 0;
        for (var mutant : List.of(wrongRun, wrongDepth, wrongPosition, wrongMove, omitted, inventedWdl, wrongClock)) {
            check.rejects("Reject a substituted/missing payoff phase " + index++, IllegalArgumentException.class,
                    () -> validateProof(mutant, quest()));
        }
    }

    private static void validateProof(JsonNode root, QuestCatalog.Quest q) {
        var plan = root.path("plan");
        var completion = root.path("completion");
        var phases = root.path("phases");
        String run = root.path("runId").asText();
        var expectedMoves = new ArrayList<String>();
        plan.path("line").forEach(move -> expectedMoves.add(move.asText()));
        if (run.isBlank() || !plan.path("runId").asText().equals(run) || !completion.path("runId").asText().equals(run)
                || completion.path("nativeExit").asInt(-1) != 0 || !plan.path("initialFen").asText().equals(q.fen())
                || !moves(q).equals(expectedMoves) || !root.path("source").path("extensionAuthor").asText().contains("not Lichess")
                || completion.path("grades").size() != q.line().size() * 2 || phases.size() != completion.path("phases").asInt()) {
            throw new IllegalArgumentException("Incomplete or foreign payoff proof");
        }
        var seen = new HashSet<String>();
        for (var grade : completion.path("grades")) {
            int ply = grade.path("ply").asInt(-1), depth = grade.path("depth").asInt(-1);
            if (ply < 0 || ply >= q.line().size() || depth != 17 && depth != 19 || !seen.add(ply + ":" + depth)
                    || !grade.path("move").asText().equals(q.line().get(ply).uci())) {
                throw new IllegalArgumentException("Changed payoff stage or depth");
            }
            String prefix = String.join(" ", moves(q).subList(0, ply));
            String position = "position fen " + q.fen() + (prefix.isEmpty() ? "" : " moves " + prefix);
            String fen = StudyFen.replay(q.fen(), q.line().subList(0, ply));
            JsonNode free = phase(phases, grade.path("freePhase").asInt(), run, depth, ply, position, fen);
            JsonNode actual = phase(phases, grade.path("actualPhase").asInt(), run, depth, ply, position, fen);
            if (!free.path("forced").isNull() || !actual.path("bestmove").asText().equals(grade.path("move").asText())
                    || !actual.path("forced").isNull() && !actual.path("forced").asText().equals(grade.path("move").asText())
                    || grade.path("loss").asDouble() != free.path("expectedPerMille").asDouble() - actual.path("expectedPerMille").asDouble()
                    || grade.path("loss").asDouble() > 50) throw new IllegalArgumentException("Unqualified actual payoff choice");
        }
    }

    private static JsonNode phase(JsonNode phases, int number, String run, int depth, int ply, String position, String fen) {
        if (number < 1 || number > phases.size()) throw new IllegalArgumentException("Missing payoff phase");
        JsonNode phase = phases.get(number - 1);
        if (!phase.path("runId").asText().equals(run) || phase.path("depth").asInt() != depth || phase.path("ply").asInt(-1) != ply
                || !phase.path("positionCommand").asText().equals(position) || !phase.path("fen").asText().equals(fen) || phase.path("pv").isEmpty()
                || !phase.path("bestmove").asText().equals(phase.path("pv").get(0).asText())) throw new IllegalArgumentException("Foreign payoff phase");
        boolean hasPosition = false, hasCommand = false, hasBestmove = false;
        String exact = null;
        for (var line : phase.path("raw")) {
            String text = line.asText();
            hasPosition |= text.equals("> " + position);
            hasCommand |= text.equals("> go depth " + depth + " nodes 10000000"
                    + (phase.path("forced").isNull() ? "" : " searchmoves " + phase.path("forced").asText()));
            hasBestmove |= text.startsWith("< bestmove " + phase.path("bestmove").asText());
            if (text.startsWith("< info depth " + depth + " ") && text.contains(" pv ")
                    && !text.contains("lowerbound") && !text.contains("upperbound")) exact = text;
        }
        if (!hasPosition || !hasCommand || !hasBestmove || exact == null) throw new IllegalArgumentException("No exact requested-horizon receipt");
        var wdl = java.util.regex.Pattern.compile("\\bwdl (\\d+) (\\d+) (\\d+)").matcher(exact);
        var score = java.util.regex.Pattern.compile("\\bscore (cp|mate) (-?\\d+)").matcher(exact);
        if (!wdl.find() || !score.find()) throw new IllegalArgumentException("Missing exact score/WDL");
        int win = Integer.parseInt(wdl.group(1)), draw = Integer.parseInt(wdl.group(2)), loss = Integer.parseInt(wdl.group(3));
        if (win + draw + loss != 1000 || phase.path("expectedPerMille").asDouble(-1) != win + draw / 2.0
                || !phase.path("score").path("type").asText().equals(score.group(1))
                || phase.path("score").path("value").asInt() != Integer.parseInt(score.group(2))
                || !exact.split(" pv ")[1].split(" ")[0].equals(phase.path("bestmove").asText())) {
            throw new IllegalArgumentException("Changed score, WDL or final root");
        }
        return phase;
    }
}
