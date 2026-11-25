package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ReviewIncrementalPersistenceComponentTest {
    private static final String[] MOVES = {"e2e4", "e7e5", "g1f3", "b8c6"};

    @Test void eachDurableMoveCollectsAndSavesOneNewNativeComparisonBeforeFinalAggregation(Checks check) {
        var disk = new MemoryPreferences();
        var store = new MatchStore(disk, Clock.systemUTC());
        var match = game(0);
        check.same("The AI game has a durable identity before collection", true, store.startMatch(1, match, false, false, false));
        String id = store.archivedId(1);
        var tasks = new ArrayList<Runnable>();
        var errors = new ArrayList<RuntimeException>();
        var collector = new ReviewCollector(tasks::add,
                request -> store.prepareReview(request.id(), request.match(), false, ignored -> { }), errors::add);
        List<ReviewJournal.Sample> earlier = List.of();
        for (int ply = 1; ply <= MOVES.length; ply++) {
            match = game(ply);
            check.same("Actual move " + ply + " is saved before its analysis starts", true, store.saveMatch(1, match, false, false, false));
            int writes = disk.commitAttempts;
            collector.submit(id, match);
            check.same("Actual move " + ply + " queues one background collection", 1, tasks.size());
            tasks.removeFirst().run();
            check.same("Actual move " + ply + " commits only its new native comparison", 1, disk.commitAttempts - writes);
            var restarted = new MatchStore(disk, Clock.systemUTC());
            var journal = restarted.readReviewWork(id, match);
            check.same("A new store sees every completed ply after move " + ply, ply, journal.completed());
            var samples = samples(journal);
            check.same("Earlier measured evidence survives move " + ply + " unchanged", earlier, samples.subList(0, earlier.size()));
            earlier = samples;
        }
        collector.close();
        check.same("Real native collection completed without failures", List.of(), errors);
        var finished = match.resign();
        check.same("Finishing preserves the same game identity", true, store.saveMatch(1, finished, false, false, false));
        var restarted = new MatchStore(disk, Clock.systemUTC());
        int writes = disk.commitAttempts;
        var review = restarted.prepareReview(id, finished, true, ignored -> { throw new AssertionError("Completed work was recalculated"); });
        check.same("Final aggregation performs no journal write", writes, disk.commitAttempts);
        check.same("Final aggregation retains all four measured comparisons", earlier.stream().map(ReviewJournal.Sample::actual).toList(),
                review.turns().stream().map(GameReview.Turn::playedPoints).toList());
        check.same("The finished review can be archived", true, restarted.saveReview(id, finished, review));
        check.same("The archived final report survives another store restart", review,
                new MatchStore(disk, Clock.systemUTC()).readReview(id, finished));
    }

    @Test void interruptionAfterTwoNativePliesResumesOnlyTheMissingSuffix(Checks check) {
        var disk = new MemoryPreferences();
        var store = new MatchStore(disk, Clock.systemUTC());
        var match = game(4);
        store.startMatch(1, match, false, false, false);
        String id = store.archivedId(1);
        int initialWrites = disk.commitAttempts;
        var observed = new ArrayList<Integer>();
        check.rejects("A simulated interruption happens after durable ply two", CancellationException.class,
                () -> store.prepareReview(id, match, false, ply -> {
                    observed.add(new MatchStore(disk, Clock.systemUTC()).readReviewWork(id, match).completed());
                    if (ply == 2) throw new CancellationException("Controlled interruption");
                }));
        check.same("Each completed ply was already durable before the interruption", List.of(1, 2), observed);
        check.same("Only two work transactions happened before interruption", 2, disk.commitAttempts - initialWrites);
        var restarted = new MatchStore(disk, Clock.systemUTC());
        var partial = restarted.readReviewWork(id, match);
        var retained = samples(partial);
        check.rejects("A partial journal cannot become a complete report", IllegalStateException.class,
                () -> GameReview.aggregate(match, partial, false));
        int beforeResume = disk.commitAttempts;
        var resumed = new ArrayList<Integer>();
        var result = restarted.prepareReview(id, match, false,
                ply -> resumed.add(new MatchStore(disk, Clock.systemUTC()).readReviewWork(id, match).completed()));
        check.same("Cached plies cause no writes, then new plies become durable", List.of(2, 2, 3, 4), resumed);
        check.same("Recovery commits exactly the two missing comparisons", 2, disk.commitAttempts - beforeResume);
        var complete = restarted.readReviewWork(id, match);
        check.same("Recovery leaves the accepted prefix byte-equivalent as values", retained, samples(complete).subList(0, 2));
        check.same("The recovered report contains every actual move", 4, result.turns().size());
    }

    @Test void controlledCachedScoresReachTheFinalReportWithoutASecondProviderOrWrite(Checks check) {
        var match = game(4).resign();
        var disk = new MemoryPreferences();
        var store = new MatchStore(disk, Clock.systemUTC());
        store.startMatch(1, match, false, false, false);
        String id = store.archivedId(1);
        var calls = new AtomicInteger();
        var journal = new ReviewJournal(match);
        for (int ply = 1; ply <= match.ply(); ply++) {
            final int index = ply;
            journal.obtain(ply, () -> {
                calls.incrementAndGet();
                var line = List.of(match.moveAt(index));
                int points = 400 + index * 13;
                return new ReviewJournal.Sample(points, points, points - 7, 4, 0, 100 + index, true, false, line, line, null);
            });
        }
        check.same("The controlled comparison provider runs once per actual move", 4, calls.get());
        check.same("Controlled fixtures are saved through the production store", true, store.saveReviewWork(id, match, journal));
        var restarted = new MatchStore(disk, Clock.systemUTC());
        int writes = disk.commitAttempts;
        var restored = restarted.readReviewWork(id, match);
        for (int ply = 1; ply <= match.ply(); ply++) restored.obtain(ply, () -> { throw new AssertionError("Provider called on cache hit"); });
        var review = restarted.prepareReview(id, match, true, ignored -> { throw new AssertionError("Unexpected analysis progress"); });
        check.same("The final report uses the exact controlled cached scores", List.of(413, 426, 439, 452),
                review.turns().stream().map(GameReview.Turn::playedPoints).toList());
        check.same("Every comparison keeps its cached common depth", List.of(4, 4, 4, 4),
                review.turns().stream().map(GameReview.Turn::depth).toList());
        check.same("Aggregation neither invokes the provider nor rewrites work", List.of(4, writes), List.of(calls.get(), disk.commitAttempts));
    }

    private static ChessMatch game(int plies) {
        return ChessMatch.restore("3|WHITE|BASIC|0|" + String.join(" ", List.of(MOVES).subList(0, plies)));
    }

    private static List<ReviewJournal.Sample> samples(ReviewJournal journal) {
        var samples = new ArrayList<ReviewJournal.Sample>();
        for (int ply = 1; ply <= journal.completed(); ply++) samples.add(journal.require(ply));
        return List.copyOf(samples);
    }
}
