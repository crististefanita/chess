package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Exact final-position proof; the earlier high-material race remains teacher-audited. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class Training060TablebaseTest {
    private static final String FINAL_FEN = "8/8/8/K3p3/4q2p/6k1/3Q4/8 w - - 4 50";
    private static final String ENDPOINT = "https://tablebase.lichess.ovh/standard?fen=";
    private static final String PIN = "9c790ce2b29585be493052a48adb02c1a25e848a39723f0fe16b33d2ba6b49cf";

    private static QuestCatalog.Quest quest() {
        return QuestCatalog.loadTraining().stream().filter(q -> q.id().equals("training-060")).findFirst().orElseThrow();
    }
    private static byte[] bytes() throws IOException {
        try (var input = Training060TablebaseTest.class.getResourceAsStream("/hard-lessons/training-060-final-syzygy.json")) {
            if (input == null) throw new IOException("Missing actual tablebase response");
            return input.readAllBytes();
        }
    }
    private static JsonNode decode(byte[] bytes) throws IOException {
        var proof = LearningGameAudit.mapper().readTree(bytes);
        if (!HardLeagueAudit.digest(proof.toString().getBytes(StandardCharsets.UTF_8)).equals(PIN)) {
            throw new IllegalArgumentException("Changed authoritative response or request context");
        }
        return proof;
    }

    @Test void exactSixPieceFinishLosesForWhiteWithEveryLegalDefenseCovered(Checks check) throws Exception {
        var q = quest();
        var proof = decode(bytes());
        validate(proof, q);
        var raw = proof.path("raw");
        check.same("The complete authored race reaches the exact six-piece FEN including clocks", FINAL_FEN,
                StudyFen.replay(q.fen(), q.line()));
        int pieces = 0;
        for (int square = 0; square < 64; square++) if (q.solvedPosition().pieceAt(square) != null) pieces++;
        check.same("Only the final six-piece position is claimed tablebase-exact", List.of(6, ChessPosition.Side.WHITE),
                List.of(pieces, q.solvedPosition().turn()));
        check.same("The actual requested-position category is a White loss, not a blessed loss", "loss", raw.path("category").asText());
        check.same("Signed DTZ and precise DTZ agree; DTM is a separate distance", List.of(-8, -8, -58),
                List.of(raw.path("dtz").asInt(), raw.path("precise_dtz").asInt(), raw.path("dtm").asInt()));
        check.same("The current quiet clock leaves enough plies for the next guaranteed zeroing", true,
                Integer.parseInt(proof.path("requestFen").asText().split(" ")[4])
                        + Math.abs(raw.path("precise_dtz").asInt()) < 100);
        check.same("All twenty-five legal White replies are in the original authoritative response", 25, raw.path("moves").size());
        for (var reply : raw.path("moves")) {
            check.same("After White " + reply.path("uci").asText() + ", side-to-move Black retains the exact win",
                    "win", reply.path("category").asText());
        }
        check.same("The first DTZ-optimal defense is the legal queen check", List.of("d2c3", "Qc3+", 7),
                List.of(raw.path("moves").get(0).path("uci").asText(), raw.path("moves").get(0).path("san").asText(),
                        raw.path("moves").get(0).path("dtz").asInt()));
    }

    @Test void proofTransportIsPortableAndChangedPositionResultOrDistanceIsRejected(Checks check) throws Exception {
        String lf = new String(bytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        check.same("LF and CRLF carry exactly the same pinned tablebase facts", decode(lf.getBytes(StandardCharsets.UTF_8)),
                decode(lf.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8)));
        var original = decode(bytes());
        var changedFacts = (ObjectNode) original.deepCopy();
        changedFacts.put("scope", "ALL_INITIAL_DEFENSES_PROVED");
        check.rejects("Transport normalization cannot widen the proof scope", IllegalArgumentException.class,
                () -> decode(LearningGameAudit.mapper().writeValueAsBytes(changedFacts)));
        var wrongFen = (ObjectNode) original.deepCopy();
        wrongFen.put("requestFen", FINAL_FEN.replace("4 50", "0 1"));
        var wrongCategory = original.deepCopy();
        ((ObjectNode) wrongCategory.path("raw")).put("category", "draw");
        var wrongDtz = original.deepCopy();
        ((ObjectNode) wrongDtz.path("raw")).put("dtz", -9).put("precise_dtz", -9);
        var wrongChild = original.deepCopy();
        ((ObjectNode) wrongChild.path("raw").path("moves").get(0)).put("category", "draw");
        var missingMove = original.deepCopy();
        ((ArrayNode) missingMove.path("raw").path("moves")).remove(0);
        int example = 0;
        for (var mutant : List.of(wrongFen, wrongCategory, wrongDtz, wrongChild, missingMove)) {
            check.rejects("Reject changed FEN/clocks/result/DTZ or incomplete move inventory " + example++,
                    IllegalArgumentException.class, () -> validate(mutant, quest()));
        }
    }

    private static void validate(JsonNode proof, QuestCatalog.Quest q) {
        String fen = StudyFen.replay(q.fen(), q.line());
        String url = proof.path("requestUrl").asText();
        var raw = proof.path("raw");
        if (!fen.equals(FINAL_FEN) || !proof.path("requestFen").asText().equals(fen)
                || !url.startsWith(ENDPOINT) || !URLDecoder.decode(url.substring(ENDPOINT.length()), StandardCharsets.UTF_8).equals(fen)
                || proof.path("httpStatus").asInt() != 200 || !raw.path("category").asText().equals("loss")
                || !raw.path("dtz").isInt() || raw.path("dtz").asInt() != -8 || raw.path("precise_dtz").asInt() != -8
                || raw.path("checkmate").asBoolean() || raw.path("stalemate").asBoolean()
                || Integer.parseInt(fen.split(" ")[4]) + Math.abs(raw.path("precise_dtz").asInt()) >= 100) {
            throw new IllegalArgumentException("Foreign, unproved or fifty-move-incompatible final outcome");
        }
        var expected = new TreeSet<>(q.solvedPosition().legalMoves().stream().map(ChessPosition.Move::uci).toList());
        var actual = new TreeSet<String>();
        for (var move : raw.path("moves")) {
            if (!actual.add(move.path("uci").asText()) || !move.path("category").asText().equals("win")
                    || !move.path("dtz").isInt() || move.path("dtz").asInt() <= 0) {
                throw new IllegalArgumentException("Foreign defense or unverified Black successor");
            }
        }
        if (actual.size() != 25 || !actual.equals(expected)) throw new IllegalArgumentException("Incomplete final legal move inventory");
    }
}
