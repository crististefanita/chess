package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ReviewJournalComponentTest {
    @Test void completeJournalAggregatesIdenticallyWithoutCallingTheSearchProviderAgain(Checks check) {
        var match = game("e2e4 e7e5");
        var work = new ReviewJournal(match);
        var first = GameReview.prepare(match, work, ignored -> { });
        check.same("Every actual ply has native evidence", 2, work.completed());
        for (int ply = 1; ply <= 2; ply++) work.obtain(ply, () -> { throw new AssertionError("Search was repeated"); });
        check.same("Final assembly preserves every score, classification, line and statistic", first, GameReview.aggregate(match, work, false));
        var restored = ReviewJournal.restore(match, work.encode());
        check.same("A real serialization round trip reuses all work", List.of("REUSED", 2), List.of(restored.restoration(), restored.completed()));
        check.same("Restored aggregation is identical", first, GameReview.aggregate(match, restored, false));
        var finished = match.resign();
        check.same("Final result, grades and eligible ratings match a fresh finished-game review", GameReview.analyze(finished, ignored -> { }),
                GameReview.aggregate(finished, restored, true));
    }

    @Test void appendBranchAndMalformedSnapshotsCannotInventACompleteReview(Checks check) {
        var original = game("e2e4 e7e5");
        var work = new ReviewJournal(original);
        GameReview.prepare(original, work, ignored -> { });
        var appended = game("e2e4 e7e5 g1f3");
        var resumed = ReviewJournal.restore(appended, work.encode());
        check.same("Appending preserves the two already analyzed plies", 2, resumed.completed());
        check.same("Aggregation refuses the unmeasured third move", true, missing(() -> GameReview.aggregate(appended, resumed, false)));
        var branch = game("e2e4 c7c5");
        var branched = ReviewJournal.restore(branch, work.encode());
        check.same("Only the common legal prefix survives a branch", List.of("TRIMMED", 1), List.of(branched.restoration(), branched.completed()));
        check.same("The changed reply still requires its own evidence", true, missing(() -> GameReview.aggregate(branch, branched, false)));
        byte[] bytes = Base64.getDecoder().decode(work.encode()); bytes[3] ^= 7;
        check.same("A stale journal version is rejected", "REJECTED", ReviewJournal.restore(original, Base64.getEncoder().encodeToString(bytes)).restoration());
        check.same("A truncated journal is rejected", "REJECTED", ReviewJournal.restore(original, work.encode().substring(0, 16)).restoration());
    }

    @Test void storeRetainsWorkAfterRestartAndRejectsAnEvictedOrChangedGame(Checks check) {
        var disk = new MemoryPreferences();
        var store = new MatchStore(disk, Clock.systemUTC());
        var match = game("e2e4");
        check.same("The AI game is durably started", true, store.startMatch(1, match, false, false, false));
        String id = store.archivedId(1);
        var work = new ReviewJournal(match); GameReview.prepare(match, work, ignored -> { });
        check.same("Native per-move work is durably saved", true, store.saveReviewWork(id, match, work));
        var restarted = new MatchStore(disk, Clock.systemUTC());
        check.same("A fresh store restores the journal", 1, restarted.readReviewWork(id, match).completed());
        check.same("The finished screen can use aggregation with no missing work", GameReview.aggregate(match, work, false),
                restarted.prepareReview(id, match, false, ignored -> { throw new AssertionError("Nothing is missing"); }));
        check.same("Replacing the slot creates a distinct game", true, restarted.startMatch(1, game("d2d4"), false, false, false));
        check.same("A late result cannot attach to the replacement game", false, restarted.saveReviewWork(id, match, work));
    }

    @Test void collectorCoalescesRapidMovesAndKeepsFailedPersistenceVisible(Checks check) {
        var tasks = new ArrayList<Runnable>();
        var seen = new ArrayList<Integer>();
        var failures = new AtomicInteger();
        var collector = new ReviewCollector(tasks::add, request -> seen.add(request.match().ply()), failure -> failures.incrementAndGet());
        collector.submit("game", game("e2e4")); collector.submit("game", game("e2e4 e7e5")); collector.submit("game", game("e2e4 e7e5 g1f3"));
        check.same("Only one background job is queued", 1, tasks.size());
        tasks.removeFirst().run();
        check.same("The latest durable checkpoint is collected", List.of(3), seen);
        collector.submit("game", game("e2e4 e7e5 g1f3"));
        check.same("Rendering the same state does not schedule another search", 0, tasks.size());
        var broken = new ReviewCollector(Runnable::run, ignored -> { throw new IllegalStateException("disk full"); }, failure -> failures.incrementAndGet());
        broken.submit("game", game("e2e4"));
        check.same("A save failure is not recorded as successful work", 1, failures.get());
        collector.close(); collector.submit("game", game("d2d4"));
        check.same("Closed UI ownership cannot restart collection", 0, tasks.size());
    }

    @Test void policyTrailingBytesForeignRootsAndIllegalEvidenceAreRejected(Checks check) {
        var match = game("e2e4");
        var work = new ReviewJournal(match); GameReview.prepare(match, work, ignored -> { });
        byte[] bytes = Base64.getDecoder().decode(work.encode()); bytes[6] ^= 1;
        check.same("Another analysis policy cannot reuse scored work", "REJECTED", ReviewJournal.restore(match, Base64.getEncoder().encodeToString(bytes)).restoration());
        byte[] extra = java.util.Arrays.copyOf(Base64.getDecoder().decode(work.encode()), Base64.getDecoder().decode(work.encode()).length + 1);
        check.same("Trailing bytes are rejected", "REJECTED", ReviewJournal.restore(match, Base64.getEncoder().encodeToString(extra)).restoration());
        check.same("An unrelated first move has no completed samples", 0, ReviewJournal.restore(game("d2d4"), work.encode()).completed());
        var good = work.require(1);
        var invalid = new ReviewJournal(match);
        check.same("An illegal principal variation is never stored", true, invalid(() -> invalid.obtain(1, () -> new ReviewJournal.Sample(
                good.best(), good.actual(), good.alternative(), good.depth(), good.sacrifice(), good.ordinaryNodes(), good.bestMove(), good.onlyMate(),
                List.of(ChessPosition.Move.fromUci("e2e5")), good.playedLine(), good.proof()))));
        check.same("Failed admission did not advance completion", 0, invalid.completed());
    }

    @Test void failedCommitRemainsMissingAndCanceledOwnershipDoesNotReportAStorageFault(Checks check) {
        var disk = new MemoryPreferences(); var store = new MatchStore(disk, Clock.systemUTC()); var match = game("e2e4");
        store.startMatch(1, match, false, false, false); String id = store.archivedId(1);
        disk.rejectCommit = true;
        check.same("A real failed transaction prevents successful preparation", true, missing(() -> store.prepareReview(id, match, false, ignored -> { })));
        check.same("A fresh store cannot observe unsaved evidence", 0, new MatchStore(disk, Clock.systemUTC()).readReviewWork(id, match).completed());
        disk.data.put("match-archive-" + id + "-work", 7);
        check.same("A wrongly typed preference is missing work, not a complete review", 0, store.readReviewWork(id, match).completed());
        disk.rejectCommit = false;
        check.same("Retry saves the missing work", 1, store.prepareReview(id, match, false, ignored -> { }).turns().size());
        var errors = new AtomicInteger(); var owner = new ReviewCollector[1];
        owner[0] = new ReviewCollector(Runnable::run, ignored -> { owner[0].close(); throw new IllegalStateException("destroyed owner"); }, ignored -> errors.incrementAndGet());
        owner[0].submit("owner", match);
        check.same("A destroyed screen receives no late failure callback", 0, errors.get());
    }

    private static ChessMatch game(String moves) { return ChessMatch.restore("3|WHITE|BASIC|0|" + moves); }
    private static boolean missing(Runnable action) { try { action.run(); return false; } catch (IllegalStateException expected) { return true; } }
    private static boolean invalid(Runnable action) { try { action.run(); return false; } catch (IllegalArgumentException expected) { return true; } }
}
