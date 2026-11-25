package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintSelectableLinesComponentTest {
    @Test void everyCachedOpeningPlaybackFitsTheThreeNativeTabSlots(Checks check) {
        var failures = new ArrayList<String>();
        int frames = 0;
        for (var opening : OpeningCatalog.load()) for (int ply = 0; ply <= opening.steps().size(); ply++) {
            var hint = OpeningReference.analyze(opening, ply);
            var board = opening.positionAt(ply);
            if (hint.count() < 1 || hint.count() > 3) failures.add(opening.id() + "/" + ply + ": count=" + hint.count());
            for (int choice = 0; choice < hint.count(); choice++) {
                hint.revealNext();
                var playback = hint.playback(board).orElseThrow();
                if (playback.lines().size() != hint.count() || !playback.lines().get(choice).move().equals(playback.selected()))
                    failures.add(opening.id() + "/" + ply + ": selected=" + choice);
            }
            frames++;
        }
        check.same("Every real cached lesson frame offers one to three selectable, correctly selected lines", List.of(), failures);
        check.same("The test covers the complete current lesson catalog", true, frames > 140);
        check.same("Native live hints retain the configured three-variant ceiling", 3, MatchAiPolicy.CURRENT.hint.maxVariants());
    }

    @Test void oneTwoAndThreeMeasuredLinesKeepTheirOrderAndCycleWithoutAnotherSearch(Checks check) throws Exception {
        var opening = OpeningCatalog.load().getFirst();
        for (int count = 1; count <= 3; count++) {
            var data = copy();
            var first = row(data);
            var all = (List<?>) first.get("lines");
            first.put("lines", new ArrayList<>(all.subList(0, count)));
            data.put("positions", List.of(first));
            var reference = OpeningReference.read(data);
            var hint = reference.analyze(opening, 0, () -> { throw new AssertionError("Unexpected live search"); });
            var roots = hint.lines().stream().map(TacticalAnalysis.Variation::move).toList();
            for (int selected = 0; selected < count; selected++) {
                hint.revealNext();
                var playback = hint.playback(opening.positionAt(0)).orElseThrow();
                check.same(count + " measured lines preserve every selectable tab in source order", roots,
                        playback.lines().stream().map(TacticalAnalysis.Variation::move).toList());
                check.same(count + " measured lines select the requested tab", roots.get(selected), playback.selected());
                check.same("Every cached alternative retains its measured common horizon", 16, playback.depth());
            }
            hint.revealNext();
            check.same(count + " lines cycle back to the first choice", roots.getFirst(), hint.revealedMove());
        }
    }

    @Test void aFourthLegalMeasuredLineIsRejectedBeforeItCanReachTheAndroidTabs(Checks check) throws Exception {
        var data = copy();
        var first = row(data);
        var lines = (List<Map<String, Object>>) first.get("lines");
        var used = new HashSet<String>();
        lines.forEach(line -> used.add(((String) line.get("moves")).split(" ")[0]));
        var fourth = ChessPosition.initial().legalMoves().stream().filter(move -> !used.contains(move.uci())).findFirst().orElseThrow();
        lines.add(Map.of("moves", fourth.uci(), "white-cp", lines.getLast().get("white-cp")));
        data.put("positions", List.of(first));
        check.same("The negative fixture supplies four distinct legal candidate roots", 4, lines.size());
        check.rejects("A fourth reference line cannot overflow the three native tab IDs", IllegalArgumentException.class,
                () -> OpeningReference.read(data));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copy() throws Exception {
        var json = new ObjectMapper();
        return json.readValue(json.writeValueAsBytes(ChessYaml.resource("opening-reference.yaml")), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> row(Map<String, Object> root) {
        return (Map<String, Object>) ((List<?>) root.get("positions")).getFirst();
    }
}
