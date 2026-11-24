package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.ChessPosition.Side;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class HardConfirmationCoverageTest {
    @TempDir Path folder;
    private static HardConfirmationLeague.Configuration config() {
        var base = HardMasterPolicy.CURRENT;
        var candidate = new HardMasterPolicy(base.engine(), "hard-a-v6", base.limits(), base.forcingReserve(), base.certificate(),
                base.evaluation(), base.rootPv(), base.pvs(), base.moveHints(), base.cutoffOrdering(), HardMasterPolicy.QuietTactics.FORCING_PINS);
        var prior = new HardMasterPolicy(base.engine(), "hard-a-v5", base.limits(), base.forcingReserve(), base.certificate(),
                base.evaluation(), base.rootPv(), base.pvs(), base.moveHints(), base.cutoffOrdering(), HardMasterPolicy.QuietTactics.NONE);
        return new HardConfirmationLeague.Configuration(candidate, prior, "BASE_A");
    }
    @Test void anIncompleteForeignOrDuplicatePairedRosterCannotPass(Checks check) {
        var one = new HardConfirmationLeague.Start("one", "family-one", 7, "");
        var two = new HardConfirmationLeague.Start("two", "family-two", 8, "");
        var starts = List.of(one, two); var complete = Set.of("one-WHITE", "one-BLACK", "two-WHITE", "two-BLACK");
        HardConfirmationLeague.requireRoster(starts, complete);
        check.same("Each start requires both declared colors", 4, complete.size());
        check.rejects("Missing member of a color pair", IllegalArgumentException.class,
                () -> HardConfirmationLeague.requireRoster(starts, Set.of("one-WHITE", "one-BLACK", "two-WHITE")));
        check.rejects("Foreign game cannot replace a missing color", IllegalArgumentException.class,
                () -> HardConfirmationLeague.requireRoster(starts, Set.of("one-WHITE", "one-BLACK", "two-WHITE", "other-BLACK")));
        check.rejects("Duplicate starts cannot manufacture paired evidence", IllegalArgumentException.class,
                () -> HardConfirmationLeague.requireRoster(List.of(one, one), Set.of("one-WHITE", "one-BLACK")));
    }
    @Test void truncatedDecisionLedgersAndChangedAccountingFailBeforeStrengthClaims(Checks check) throws Exception {
        var start = new HardConfirmationLeague.Start("one", "family-one", 7, "");
        Path game = folder.resolve("games/one-WHITE"); Files.createDirectories(game);
        String row = "0\tCANDIDATE\te2e4\t1\t1\tTOTAL\thard-a-v6\tCOMPLETED\tNOT_RUN\t0\tEXACT\te2e4";
        Path ledger = game.resolve("decisions.tsv"); Files.write(ledger, List.of(HardConfirmationLeague.DECISION_HEADER, row));
        var partial = HardConfirmationLeague.readGame(folder, config(), start, Side.WHITE, false);
        check.same("Partial legal play remains resumable but is not a completed league task", 1, partial.match().ply());
        check.rejects("Early truncation cannot be called capped or complete", IllegalArgumentException.class,
                () -> HardConfirmationLeague.readGame(folder, config(), start, Side.WHITE, true));
        Files.write(ledger, List.of(HardConfirmationLeague.DECISION_HEADER, row.replace("TOTAL", "LEGACY_EXTRA")));
        check.rejects("Legacy extra nodes cannot be relabeled as candidate totals", IllegalArgumentException.class,
                () -> HardConfirmationLeague.readGame(folder, config(), start, Side.WHITE, false));
        Files.write(ledger, List.of(HardConfirmationLeague.DECISION_HEADER, row.replace("\tEXACT\t", "\tLOWER\t")));
        check.rejects("A substituted lower bound cannot become a public exact decision", IllegalArgumentException.class,
                () -> HardConfirmationLeague.readGame(folder, config(), start, Side.WHITE, false));
        Files.write(ledger, List.of(HardConfirmationLeague.DECISION_HEADER, row.replace("\tEXACT\t", "\tABSENT\t")));
        check.rejects("An absent proof cannot be inferred from the completed stop label", IllegalArgumentException.class,
                () -> HardConfirmationLeague.readGame(folder, config(), start, Side.WHITE, false));
        Files.write(ledger, List.of(HardConfirmationLeague.DECISION_HEADER, row.replace("COMPLETED", "BOOK")));
        check.rejects("A book action cannot invent a searched exact score", IllegalArgumentException.class,
                () -> HardConfirmationLeague.readGame(folder, config(), start, Side.WHITE, false));
        var legacy = new HardConfirmationLeague.Configuration(config().candidate(), config().baseline(), "LEGACY");
        Path nativeFolder = folder.resolve("games/one-BLACK"); Files.createDirectories(nativeFolder);
        String nativeRow = row.replace("CANDIDATE", "REFERENCE").replace("hard-a-v6", "legacy-v1")
                .replace("TOTAL", "LEGACY_EXTRA").replace("COMPLETED", "LEGACY");
        Path nativeLedger = nativeFolder.resolve("decisions.tsv");
        Files.write(nativeLedger, List.of(HardConfirmationLeague.DECISION_HEADER, nativeRow));
        check.rejects("Uncaptured native analysis cannot borrow an exact bound", IllegalArgumentException.class,
                () -> HardConfirmationLeague.readGame(folder, legacy, start, Side.BLACK, false));
        Files.write(nativeLedger, List.of(HardConfirmationLeague.DECISION_HEADER, nativeRow.replace("\t0\tEXACT\t", "\t_\tABSENT\t")));
        var actualNative = HardConfirmationLeague.readGame(folder, legacy, start, Side.BLACK, false);
        check.same("A native reported extra cost keeps its distinct accounting", "LEGACY_EXTRA", actualNative.referenceAccounting());
        Files.write(ledger, List.of(HardConfirmationLeague.DECISION_HEADER, row.replace("e2e4", "e2e5")));
        check.rejects("Illegal root and PV are rejected", IllegalArgumentException.class,
                () -> HardConfirmationLeague.readGame(folder, config(), start, Side.WHITE, false));
        Files.write(ledger, List.of(HardConfirmationLeague.DECISION_HEADER, row, row));
        check.rejects("A repeated ply cannot hide a missing decision", IllegalArgumentException.class,
                () -> HardConfirmationLeague.readGame(folder, config(), start, Side.WHITE, false));
    }
    @Test void unfinishedGamesKeepOpenEloBoundsAndChangedBudgetsAreRejected(Checks check) {
        var config = config(); var games = new ArrayList<HardConfirmationLeague.Game>();
        for (String name : List.of("one", "two")) {
            var start = new HardConfirmationLeague.Start(name, "family-" + name, 7, "");
            for (var color : Side.values()) games.add(new HardConfirmationLeague.Game(name + "-" + color, start, color,
                    HardConfirmationLeague.replay("", 7), false, 0, 0, 0, "TOTAL"));
        }
        var summary = HardConfirmationLeague.summary(config, games).get(1).split("\t");
        check.same("Four unfinished games remain explicitly unfinished", "4", summary[4]);
        check.same("No finite lower Elo claim can be made", "-Infinity", summary[6]);
        check.same("No finite upper Elo claim can be made", "Infinity", summary[7]);
        var valid = HardConfirmationLeague.summary(config, games);
        var changedSummary = List.of(valid.get(0), valid.get(1).replace("0.000", "100.000"));
        check.rejects("A positive Elo edited into an unknown sample cannot pass", IllegalArgumentException.class,
                () -> HardConfirmationLeague.requireSummary(config, games, changedSummary));
        var base = config.baseline();
        var changed = new HardMasterPolicy(base.engine(), base.id(), new HardCandidateSearch.Limits(base.limits().maxDepth(),
                base.limits().quiescencePlies(), base.limits().nodeLimit() + 1, base.limits().tableEntries()), base.forcingReserve(),
                base.certificate(), base.evaluation(), base.rootPv(), base.pvs(), base.moveHints(), base.cutoffOrdering(), base.quietTactics());
        check.rejects("The incremental reference cannot silently change its node budget", IllegalArgumentException.class,
                () -> new HardConfirmationLeague.Configuration(config.candidate(), changed, "BASE_A"));
    }
    @Test void generationIsDeterministicAndExcludesEquivalentPreviousStarts(Checks check) {
        var first = HardConfirmationLeague.generate(Set.of());
        check.same("The fixed seed creates one hundred starts without playing an engine", 100, first.size());
        check.same("Regeneration preserves all exact histories and opening choices", first, HardConfirmationLeague.generate(Set.of()));
        var excluded = HardConfirmationLeague.replay(first.get(0).prefix(), first.get(0).choice()).position().searchKey();
        var next = HardConfirmationLeague.generate(Set.of(excluded));
        check.same("An equivalent previous board cannot be called a new start", false,
                next.stream().anyMatch(start -> HardConfirmationLeague.replay(start.prefix(), start.choice()).position().searchKey().equals(excluded)));
    }
    @Test void frozenCorporaAndTheirCopiedExclusionsCannotChangeBetweenPhases(Checks check) throws Exception {
        Path prior = folder.resolve("prior.tsv");
        Files.write(prior, List.of("id\tfamily\tuciPosition", "prior\tprior\tstartpos moves e2e4 e7e5"));
        Path out = folder.resolve("frozen"); HardConfirmationLeague.freeze(out, config(), List.of(prior));
        check.same("The proposal freezes and validates the whole declared corpus before games", 100, HardConfirmationLeague.load(out, config()).size());
        Files.writeString(out.resolve("excluded/000.tsv"), "edited", StandardOpenOption.APPEND);
        check.rejects("Copied prior starts retain their own verified fingerprints", IllegalArgumentException.class,
                () -> HardConfirmationLeague.load(out, config()));
        Files.copy(prior, out.resolve("excluded/000.tsv"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(out.resolve("frozen-starts.tsv"), "edited", StandardOpenOption.APPEND);
        check.rejects("The post-freeze start list cannot be substituted", IllegalArgumentException.class,
                () -> HardConfirmationLeague.load(out, config()));
    }
    @Test void runnerIdentityBindsActualReaderAndStatisticsButNotIndependentTests(Checks check) throws Exception {
        for (String name : List.of("HardConfirmationLeague.class", "HardConfirmationStudyTest.class", "PairedElo.class", "PairedElo$Pair.class"))
            Files.write(folder.resolve(name), new byte[] {1, 2, 3});
        Files.write(folder.resolve("IndependentTest.class"), new byte[] {4});
        String before = HardConfirmationLeague.harnessFingerprint(folder);
        Files.write(folder.resolve("IndependentTest.class"), new byte[] {5});
        check.same("An unrelated test edit does not reidentify the actual league", before, HardConfirmationLeague.harnessFingerprint(folder));
        Files.write(folder.resolve("PairedElo$Pair.class"), new byte[] {6});
        check.same("Statistics nested bytecode is part of the real harness identity", false, before.equals(HardConfirmationLeague.harnessFingerprint(folder)));
        Files.delete(folder.resolve("HardConfirmationStudyTest.class"));
        check.rejects("Missing actual study bytecode cannot yield a valid identity", IllegalArgumentException.class,
                () -> HardConfirmationLeague.harnessFingerprint(folder));
    }
    @Test void pgnUsesSanForCastlingEnPassantPromotionAndAmbiguousPieces(Checks check) {
        check.same("Orthodox short castling", "O-O", HardConfirmationLeague.san(ChessPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"), ChessPosition.Move.fromUci("e1g1")));
        check.same("En passant pawn file and capture marker", "exd6", HardConfirmationLeague.san(ChessPosition.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2"), ChessPosition.Move.fromUci("e5d6")));
        check.same("Promotion and mate suffix", "f8=Q#", HardConfirmationLeague.san(ChessPosition.fromFen("7k/5P2/6K1/8/8/8/8/8 w - - 0 1"), ChessPosition.Move.fromUci("f7f8q")));
        check.same("Same-rank knights need origin file", "Nbd4", HardConfirmationLeague.san(ChessPosition.fromFen("4k3/8/8/8/8/1N3N2/8/4K3 w - - 0 1"), ChessPosition.Move.fromUci("b3d4")));
    }
}