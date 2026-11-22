package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchListsProjectionTest {
    @TempDir Path output;

    @Test void theEleventhGameAndResultKeepTheTwoMenusIndependentAcrossRestart(Checks check) throws Exception {
        var disk = new MatchLedgerPreferences();
        var clock = new AtomicLong(1_700_000_000_000L);
        var ids = new AtomicInteger();
        var store = new MatchStore(disk, clock::incrementAndGet, () -> "game-" + ids.incrementAndGet());
        var active = ChessMatch.restore("1|e2e4");
        var finished = ChessMatch.restore("1|f2f3 e7e5 g2g4 d8h4");
        for (int slot = 1; slot <= MatchSlots.LIMIT; slot++) {
            check.same("Saved game " + slot + " commits before the next allocation", true,
                    store.startMatch(slot, active, false, true, false));
        }
        check.same("The eleventh game replaces exactly the oldest active slot", 1, store.slotForNewMatch());
        check.same("The replacement preserves ten active rows", true, store.startMatch(1, active, false, true, false));
        check.same("The new game sorts first, and the other nine stay in creation order",
                List.of(1, 10, 9, 8, 7, 6, 5, 4, 3, 2), store.savedMatchSlots());

        String oldestResult = null;
        for (int result = 1; result <= MatchArchive.LIMIT + 1; result++) {
            check.same("Completed result " + result + " commits before recycling its slot", true,
                    store.saveMatch(1, finished, false, true, false));
            if (oldestResult == null) oldestResult = store.archivedId(1);
            check.same("The completed game leaves Saved and enters Finished", List.of(9, Math.min(result, 10)),
                    List.of(store.savedMatchSlots().size(), store.archivedMatches().size()));
            check.same("The freed slot is reused for the next active game", true,
                    store.startMatch(1, active, false, true, false));
        }
        var reopened = new MatchStore(new MatchLedgerPreferences(disk.snapshot()), clock::incrementAndGet,
                () -> "unused");
        check.same("The oldest result and its archive data are gone after the eleventh finish", false,
                reopened.hasArchived(oldestResult));
        check.same("Restart retains separate ten-row Saved and Finished menus", List.of(10, 10),
                List.of(reopened.savedMatchSlots().size(), reopened.archivedMatches().size()));
        check.same("The newest active game remains first after restart", 1, reopened.savedMatchSlots().getFirst());
        check.same("The newest result remains first after restart", "game-21", reopened.archivedMatches().getFirst().id());

        check.same("The shared Saved projection accepts only a playable game", MatchListEntry.Status.IN_PROGRESS,
                MatchListEntry.saved(1, reopened.readSavedMatch(1), reopened.startedAt(1)).status());
        check.same("The shared Finished projection accepts only a completed result", true,
                reopened.archivedMatches().stream().map(MatchListEntry::finished)
                        .noneMatch(entry -> entry.status() == MatchListEntry.Status.IN_PROGRESS));
    }

    @Test
    @EnabledIfSystemProperty(named = "chess.web.bundle", matches = ".+")
    void packagedWebListsRetainTenIndependentRowsAfterReload(Checks check) throws Exception {
        var root = ProjectRoot.locate();
        var log = output.resolve("match-lists-worker.log");
        var process = new ProcessBuilder(System.getProperty("chess.web.node", "node"),
                root.resolve("web/tests/match-lists-worker.mjs").toString(), System.getProperty("chess.web.bundle"))
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        check.same("Packaged worker completes the 11 plus 11 boundary without a browser", true, completed);
        check.same("Saved and Finished remain separate after the worker reload", 0, completed ? process.exitValue() : -1);
        check.same("The worker verified both ten-entry lists and their replacement order", true,
                Files.readString(log).contains("PASS Ten Saved and ten Finished rows remain separate"));
    }
}
