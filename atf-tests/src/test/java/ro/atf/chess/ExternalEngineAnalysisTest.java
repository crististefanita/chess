package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static ro.atf.chess.ChessPosition.Move.fromUci;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ExternalEngineAnalysisTest {
    static final ExternalEngineAnalysis.Provider PROVIDER = new ExternalEngineAnalysis.Provider(
            "stockfish-19.0.1-full-single", "19.0.1",
            "991bede2f253ebb08ff01fcc48270f4b9c8e66b12d5bd5f8d6f52eb3e3b3a740",
            "0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6",
            "{\"threads\":1,\"hashMb\":32,\"nodes\":200000,\"limitStrength\":false}");

    static ChessMatch game(String moves) { return ChessMatch.restore("2|analysis|" + moves); }
    static ExternalEngineAnalysis.Line line(int rank, int score, String moves) {
        return new ExternalEngineAnalysis.Line(rank, 12, score, null, List.of(500, 300, 200),
                java.util.Arrays.stream(moves.split(" ")).map(ChessPosition.Move::fromUci).toList(), 200001L, 18, 100L);
    }
    static ExternalEngineAnalysis.Batch batch(ChessMatch match, int ply, ExternalEngineAnalysis.Line... lines) {
        return new ExternalEngineAnalysis.Batch(ExternalEngineAnalysis.context(match, ply), PROVIDER, 12, List.of(lines));
    }
    static ExternalEngineAnalysis.Review observations(ChessMatch match) {
        var turns = new ArrayList<ExternalEngineAnalysis.ReviewedTurn>();
        for (int p = 1; p <= match.ply(); p++) {
            var b = batch(match, p - 1, line(1, 20, match.moveAt(p).uci()));
            turns.add(new ExternalEngineAnalysis.ReviewedTurn(p, b, b));
        }
        return new ExternalEngineAnalysis.Review(ExternalEngineAnalysis.context(match, match.ply()).contextKey(), turns);
    }

    @Test void sendsFullHistoryAndAllFenFields(Checks c) {
        var m = game("e2e4 a7a6 e4e5 d7d5"); var ctx = ExternalEngineAnalysis.context(m, 4);
        c.same("Full prefix reaches the engine", List.of("e2e4", "a7a6", "e4e5", "d7d5"), ctx.moves());
        c.same("En passant and both clocks remain explicit", "w KQkq d6 0 3", String.join(" ", ctx.rootFen().split(" ")).substring(ctx.rootFen().indexOf(' ') + 1));
        c.same("Immediate en passant remains legal", true, ctx.legalMoves().contains("e5d6"));
        var cycle = game("g1f3 g8f6 f3g1 f6g8");
        c.same("Same placement with different history is another request", false,
                ExternalEngineAnalysis.context(ChessMatch.start(), 0).contextKey().equals(ExternalEngineAnalysis.context(cycle, 4).contextKey()));
        var castle = game("e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 e1g1");
        c.same("Castling rights and fullmove survive", "b kq - 5 4", ExternalEngineAnalysis.context(castle, 7).rootFen().split(" ", 2)[1]);
    }

    @Test void rebindsOnlyTheSameMeasuredPrefixWithoutRelabellingTheOriginal(Checks c) {
        var first = game("e2e4"); var later = game("e2e4 e7e5");
        var observed = batch(first, 1, line(1, 20, "e7e5 g1f3"));
        var reused = ExternalEngineAnalysis.rebind(later, 1, observed);
        c.same("A later checkpoint can reuse the exact measured history", ExternalEngineAnalysis.context(later, 1), reused.context());
        c.same("Measured provider, horizon and raw line remain intact", observed.lines(), reused.lines());
        c.same("The original measurement retains its own checkpoint identity", ExternalEngineAnalysis.context(first, 1), observed.context());
        c.rejects("Another historical prefix cannot borrow the observation", IllegalArgumentException.class,
                () -> ExternalEngineAnalysis.rebind(game("d2d4"), 1, observed));
        var initial = batch(ChessMatch.start(), 0, line(1, 20, "e2e4"));
        c.rejects("Equal placement after a repetition is not the measured prefix", IllegalArgumentException.class,
                () -> ExternalEngineAnalysis.rebind(game("g1f3 g8f6 f3g1 f6g8"), 4, initial));
    }

    @Test void preservesBlackPerspectiveAndFiltersWeakHints(Checks c) {
        var m = game("e2e4");
        var b = ExternalEngineAnalysis.batch(m, 1, batch(m, 1, line(1, 45, "e7e5 g1f3"), line(2, -400, "a7a6")).json());
        var h = MatchHint.external(m.positionAt(1), b);
        c.same("Black advantage is negative for White", -45, h.evaluation(m.positionAt(1)).whiteCentipawns());
        c.same("A much weaker alternative is retained only as evidence", 1, h.count());
        c.same("All observed PVs remain archived", 2, h.external().orElseThrow().lines().size());
        c.same("Raw work is preserved without pretending a strict node ceiling", 200001L, b.best().nodes());
        var mate = new ExternalEngineAnalysis.Line(1, 12, null, 2, List.of(1000, 0, 0), List.of(fromUci("d8h4")));
        c.same("Black mate estimate is signed for White", -2, mate.evaluation(ChessPosition.Side.BLACK).whiteMateMoves());
    }

    @Test void offersAtMostThreeNearBestStockfishRecommendations(Checks c) {
        var game = ChessMatch.start();
        var observed = batch(game, 0,
                line(1, 70, "e2e4 e7e5"), line(2, 55, "d2d4 d7d5"), line(3, 30, "c2c4 e7e5"),
                line(4, 20, "g1f3 d7d5"), line(5, 9, "b1c3 d7d5"));
        var validated = ExternalEngineAnalysis.batch(game, 0, observed.json());
        var hint = MatchHint.external(game.position(), validated);
        c.same("Only the top three close-scoring recommendations are selectable",
                List.of("e2e4", "d2d4", "c2c4"), hint.lines().stream().map(item -> item.move().uci()).toList());
        c.same("The raw Stockfish observation remains intact for audit", 5, hint.external().orElseThrow().lines().size());
        c.same("The capped result can be cycled without starting another search", 3, hint.count());
        hint.revealNext();
        c.same("The first recommendation can be selected", "e2e4", hint.revealedMove().uci());
        hint.revealNext();
        c.same("The second recommendation can be selected", "d2d4", hint.revealedMove().uci());
        hint.revealNext();
        c.same("The third recommendation can be selected", "c2c4", hint.revealedMove().uci());
    }

    @Test void rejectsForeignOrIncompleteObservations(Checks c) {
        var m = ChessMatch.start(); String valid = batch(m, 0, line(1, 30, "e2e4 e7e5")).json();
        for (String bad : List.of(valid.replace("e2e4", "e2e5"), valid.replace("EXACT", "LOWER"),
                valid.replace("\"rank\":1", "\"rank\":2"), valid.replace("\"depth\":12", "\"depth\":11" ).replaceFirst("\"depth\":11", "\"depth\":12"),
                valid.replace(ExternalEngineAnalysis.context(m, 0).contextKey(), "foreign"), valid.replace("e7e5", "e7e6x"))) {
            c.rejects("Invalid context, PV or common horizon is rejected", IllegalArgumentException.class, () -> ExternalEngineAnalysis.batch(m, 0, bad));
        }
        c.rejects("Reduced strength is never accepted as coaching", IllegalArgumentException.class,
                () -> new ExternalEngineAnalysis.Provider(PROVIDER.providerID(), PROVIDER.version(), PROVIDER.buildSha256(), PROVIDER.sourceCommit(), PROVIDER.optionsIdentity().replace("false", "true")));
    }

    @Test void externalReviewIsUnratedAndTamperEvident(Checks c) {
        var m = game("f2f3 e7e5 g2g4 d8h4"); var review = GameReview.external(m, observations(m));
        String encoded = GameReviewCodec.encode(m, review);
        c.same("Every played move has matched free and forced evidence", 4, review.turns().size());
        c.same("External codec round trip preserves raw observations", observations(m).json(), GameReviewCodec.decode(m, encoded).external().json());
        c.same("Manual game does not invent an Elo", true, review.white().rating().isEmpty() && review.black().rating().isEmpty());
        byte[] bytes = Base64.getDecoder().decode(encoded); bytes[bytes.length - 33] ^= 1;
        c.rejects("Modified raw JSON is rejected", IllegalArgumentException.class, () -> GameReviewCodec.decode(m, Base64.getEncoder().encodeToString(bytes)));
        c.rejects("Truncated digest is rejected", IllegalArgumentException.class, () -> GameReviewCodec.decode(m, encoded.substring(0, encoded.length() - 4)));
        c.rejects("Review from another checkpoint is rejected", IllegalArgumentException.class,
                () -> GameReviewCodec.decode(game("e2e4 e7e5 f2f3 d8h4"), encoded));
        var turns = observations(m).turns().subList(0, 3);
        c.rejects("Missing move cannot become a green review", IllegalArgumentException.class,
                () -> GameReview.external(m, new ExternalEngineAnalysis.Review(observations(m).contextKey(), turns)));
    }

    @Test void explicitReanalysisPreservesTheArchiveAndRollsBackOnFailure(Checks c) {
        var m = game("f2f3 e7e5 g2g4 d8h4"); var disk = new MemoryPreferences();
        var store = new MatchStore(disk, () -> 1234L, () -> "fixture-game");
        c.same("Finished match saves once", true, store.startMatch(1, m, false, true, true));
        String id = store.archivedId(1), digest = GameReviewCodec.checkpointDigest(m);
        var original = store.readArchivedMatch(id); var nativeReview = GameReview.analyzeLine(m, p -> {});
        String legacy = GameReviewCodec.encode(m, nativeReview);
        c.same("Native v3 stays native", null, GameReviewCodec.decode(m, legacy).external());
        c.same("Original review saves", true, store.saveReviewRecord(id, m, legacy, digest));
        c.same("Opening legacy does not rewrite its bytes", legacy, store.readReviewRecord(id).review());
        String external = GameReviewCodec.encode(m, GameReview.external(m, observations(m)));
        var before = new HashMap<>(disk.data); disk.rejectCommit = true;
        c.same("Storage failure is surfaced", false, store.saveExternalReviewRecord(id, m, external, digest));
        c.same("Storage failure preserves every ledger key", before, disk.data); disk.rejectCommit = false;
        c.same("Explicit replacement succeeds", true, store.saveExternalReviewRecord(id, m, external, digest));
        var reopened = store.readArchivedMatch(id);
        c.same("ID, match, orientation and finish date stay intact", List.of(original.id(), original.match().checkpoint(), original.blackBelow(), original.finishedAt()),
                List.of(reopened.id(), reopened.match().checkpoint(), reopened.blackBelow(), reopened.finishedAt()));
        c.same("Explicitly saved analysis marks the archive reviewed", true, reopened.reviewed());
        c.same("Reopened analysis retains the provider", PROVIDER, GameReviewCodec.decode(m, store.readReviewRecord(id).review()).external().provider());
    }
}
