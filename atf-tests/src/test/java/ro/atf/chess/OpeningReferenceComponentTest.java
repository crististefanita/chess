package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Side;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class OpeningReferenceComponentTest {
    @Test void everyCatalogFrameHasMeasuredScoresLegalLinesAndWhitePerspectiveDeltaWithoutSearch(Checks check) {
        var reference = OpeningReference.read(ChessYaml.resource("opening-reference.yaml"));
        int frames = 0;
        for (var opening : OpeningCatalog.load()) for (int ply = 0; ply <= opening.steps().size(); ply++) {
            var hint = reference.analyze(opening, ply, () -> { throw new AssertionError("Unexpected live search"); });
            var evaluation = hint.evaluation(opening.positionAt(ply));
            var source = hint.reference().orElseThrow();
            check.same(opening.id() + "/" + ply + " identifies the measured reference", List.of("Stockfish 19", 16), List.of(source.engine(), source.depth()));
            int before = ply == 0 ? evaluation.whiteCentipawns()
                    : reference.analyze(opening, ply - 1, () -> { throw new AssertionError(); }).evaluation(opening.positionAt(ply - 1)).whiteCentipawns();
            check.same("Delta always means white score after minus before", evaluation.whiteCentipawns() - before, source.deltaWhiteCentipawns());
            check.same("The bar and leader use the same measured score", evaluation.whiteCentipawns(),
                    hint.first().score() * (opening.positionAt(ply).turn() == Side.WHITE ? 1 : -1));
            for (int index = 0; index < hint.count(); index++) {
                hint.revealNext();
                var board = opening.positionAt(ply);
                for (var move : hint.revealed().line()) board = board.play(move);
                check.same("The stored continuation is immediately replayable at the common horizon", 16, hint.playback(opening.positionAt(ply)).orElseThrow().depth());
            }
            check.same("A reference is not relabeled as a native tactical proof", true, hint.proof().isEmpty());
            frames++;
        }
        check.same("All lessons and their final displayed frames are included", true, frames > 140);
    }

    @Test void aMissingLeafUsesExactlyOneLiveAnalysisAndDoesNotReuseItsParent(Checks check) throws Exception {
        var root = copy();
        var positions = (List<?>) root.get("positions");
        var last = (Map<?, ?>) positions.getLast();
        String missing = (String) last.get("moves");
        ((List<?>) positions).removeLast();
        var reference = OpeningReference.read(root);
        var opening = OpeningCatalog.load().stream().filter(o -> OpeningReference.prefix(o, o.steps().size()).equals(missing)).findFirst().orElseThrow();
        var calls = new AtomicInteger();
        var fallback = new MatchHint(List.of());
        check.same("Only the missing exact frame calls the live provider", fallback,
                reference.analyze(opening, opening.steps().size(), () -> { calls.incrementAndGet(); return fallback; }));
        check.same("The fallback executes once", 1, calls.get());
        check.same("The preceding known frame remains a reference", true,
                reference.analyze(opening, opening.steps().size() - 1, () -> { throw new AssertionError(); }).reference().isPresent());
    }

    @Test void rejectsWrongDeltaIllegalPvDuplicatePrefixAndUnknownProducerFields(Checks check) throws Exception {
        var delta = copy(); row(delta, 1).put("delta-white-cp", 9999);
        check.same("An inconsistent signed delta is rejected", true, rejected(delta));
        var illegal = copy(); var lines = (List<Map<String, Object>>) row(illegal, 0).get("lines"); lines.getFirst().put("moves", "e2e5");
        check.same("An illegal stored continuation is rejected", true, rejected(illegal));
        var duplicate = copy(); ((List<Object>) duplicate.get("positions")).add(row(duplicate, 0));
        check.same("Duplicate prefixes cannot replace measured data", true, rejected(duplicate));
        var producer = copy(); ((Map<String, Object>) producer.get("source")).put("unverified", true);
        check.same("Undeclared producer fields are rejected", true, rejected(producer));
        var overflow = copy(); row(overflow, 0).put("white-cp", Integer.MIN_VALUE);
        check.same("A centipawn overflow cannot become a reference score", true, rejected(overflow));
    }

    @Test void positionRightsAndTurnComeFromTheEntireLegalPrefixAndEachCursorIsIndependent(Checks check) {
        var opening = OpeningCatalog.load().getFirst();
        var first = OpeningReference.analyze(opening, 12);
        var again = OpeningReference.analyze(opening, 12);
        first.revealNext();
        check.same("Reopening the same reference does not inherit a reveal cursor", 0, again.number());
        check.same("Both cursors have the same measured leader", first.first(), again.first());
        check.same("A continuation cannot be replayed on an unrelated board", true, first.playback(ChessPosition.initial()).isEmpty());
    }

    @Test void opponentRootsAreTheStoredCloseStockfishLinesForThatExactHistory(Checks check) {
        var opening = OpeningCatalog.load().getFirst();
        int ply = 4;
        String prefix = OpeningReference.prefix(opening, ply);
        var board = opening.positionAt(ply);
        var measured = OpeningReference.analyze(opening, ply).lines().stream().map(TacticalAnalysis.Variation::move).toList();
        var playable = OpeningReference.playingMoves(prefix, board);
        check.same("Opponent options reuse the measured Stockfish roots in their evaluated order", measured, playable);
        check.same("The displayed opening reference and opponent selection share the maximum-three contract", true,
                !playable.isEmpty() && playable.size() <= 3);
        check.same("A different board cannot borrow roots from an exact history key", List.of(),
                OpeningReference.playingMoves(prefix, ChessPosition.initial()));
        var recorded = ChessMatch.restore("2|analysis|e2e4 e7e5 g1f3 b8c6 f1b5 a7a6").position();
        var transposed = ChessMatch.restore("2|analysis|e2e4 e7e5 g1f3 a7a6 f1b5 b8c6").position();
        check.same("The control really reaches the same board through another legal history", recorded.searchKey(), transposed.searchKey());
        check.same("A transposed board with a different history does not borrow Stockfish roots", List.of(),
                OpeningReference.playingMoves("e2e4 e7e5 g1f3 a7a6 f1b5 b8c6", transposed));
    }

    private static Map<String, Object> copy() throws Exception {
        var json = new ObjectMapper();
        return json.readValue(json.writeValueAsBytes(ChessYaml.resource("opening-reference.yaml")), Map.class);
    }
    private static Map<String, Object> row(Map<String, Object> root, int index) { return (Map<String, Object>) ((List<?>) root.get("positions")).get(index); }
    private static boolean rejected(Map<?, ?> root) {
        try { OpeningReference.read(root); return false; } catch (IllegalArgumentException expected) { return true; }
    }
}
