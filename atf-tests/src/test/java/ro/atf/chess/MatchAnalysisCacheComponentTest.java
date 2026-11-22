package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchAnalysisCacheComponentTest {
    @Test void retainsOnlyCompleteBoundSourceHintsAndEvictsLeastRecentlyUsedPositions(Checks check)
            throws ReflectiveOperationException {
        var source = ChessMatch.restore("2|analysis|e2e4").position();
        var complete = MatchHint.analyze(source);
        check.same("The cache fixture has a nonempty complete proof", true,
                complete.count() > 0 && complete.proof().filter(CoachingProof.Work::complete).isPresent());

        var cache = new MatchAnalysisCache();
        cache.remember("same-match", 1, source, complete);
        complete.revealNext();
        var hit = cache.find("same-match", 1).orElseThrow();
        check.same("A cache hit retains the exact source and hint objects", true,
                hit.position() == source && hit.hint() == complete);
        check.same("A cache hit resets the reveal cursor", 0, hit.hint().number());
        hit.hint().revealNext();
        check.same("The retained position still authorizes the playable hint line", true,
                hit.hint().preview(hit.position()).isPresent());

        check.same("A changed match checkpoint invalidates the old result", true,
                cache.find("changed-match", 1).isEmpty());

        var unproved = new MatchHint(List.of(new TacticalAnalysis.Variation(
                List.of(ChessPosition.Move.fromUci("e2e4")), 0, 0, TacticalAnalysis.Ending.HORIZON)));
        cache.remember("unproved-match", 1, source, unproved);
        check.same("A nonempty hint without proof is never cached", true,
                cache.find("unproved-match", 1).isEmpty());

        var incompleteWork = new CoachingProof.Work(CoachingProof.Status.NODE_LIMIT, 0, 0, 1, 1, 0, 0, 0, 0,
                source.searchKey().toString(), List.of(source.legalMoves().getFirst()), 0, 0, null, 0);
        var incomplete = incompleteHint(source, complete, incompleteWork);
        check.same("The negative fixture is a nonempty incomplete proof", true,
                incomplete.count() > 0 && incomplete.proof().filter(CoachingProof.Work::complete).isEmpty());
        cache.remember("incomplete-match", 1, source, incomplete);
        check.same("Incomplete proof work is never cached", true,
                cache.find("incomplete-match", 1).isEmpty());

        for (int ply = 0; ply < 64; ply++) cache.remember("bounded-match", ply, source, complete);
        check.same("Reading a position refreshes its LRU recency", true,
                cache.find("bounded-match", 0).isPresent());
        cache.remember("bounded-match", 64, source, complete);
        check.same("The refreshed position survives the 64-position limit", true,
                cache.find("bounded-match", 0).isPresent());
        check.same("The least recently used position is evicted", true,
                cache.find("bounded-match", 1).isEmpty());
        check.same("The newest position remains cached", true,
                cache.find("bounded-match", 64).isPresent());
        cache.clear();
        check.same("Explicit clear removes the last cached position", true,
                cache.find("bounded-match", 64).isEmpty());
    }

    private static MatchHint incompleteHint(ChessPosition position, MatchHint complete, CoachingProof.Work work)
            throws ReflectiveOperationException {
        Constructor<MatchHint> constructor = MatchHint.class.getDeclaredConstructor(ChessPosition.class, List.class,
                TacticalAnalysis.SearchReport.class, List.class, CoachingProof.Work.class);
        constructor.setAccessible(true);
        return constructor.newInstance(position, List.of(complete.first()), null, List.of(), work);
    }
}
