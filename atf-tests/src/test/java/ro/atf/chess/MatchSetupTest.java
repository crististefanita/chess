package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.ColorChoice;
import ro.atf.chess.MatchSetup.Level;


@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchSetupTest {
    private record ColorCase(String id, ColorChoice choice, boolean randomBlack, Side side, int draws,
                             boolean blackBelow, boolean awaitingBot, Level level) { }

    @Test void sharedColorChoicesKeepTheResolvedBoardAndOpeningTurn(Checks check) throws IOException {
        ColorCase[] cases;
        try (var fixture = MatchSetupTest.class.getResourceAsStream("/match-color-choices.json")) {
            cases = new ObjectMapper().readValue(fixture, ColorCase[].class);
        }
        check.same("The shared fixture covers both fixed colors and both random outcomes", 4, cases.length);
        for (var example : cases) {
            var draws = new AtomicInteger();
            var side = example.choice().resolve(() -> {
                draws.incrementAndGet();
                return example.randomBlack();
            });
            var match = ChessMatch.start(new MatchSetup(side, example.level(), true));
            var preferences = new MemoryPreferences();
            var setupStore = store(preferences);
            check.same(example.id() + " writes the new match", true,
                    setupStore.startMatch(1, match, side == Side.BLACK, false, false));
            var reopened = store(preferences);
            var restored = reopened.readMatch(1);
            check.same(example.id() + " keeps the resolved color, level and orientation",
                    List.of(new MatchSetup(example.side(), example.level(), true), example.blackBelow()),
                    List.of(restored.setup(), reopened.matchBlackBelow(1)));
            check.same(example.id() + " keeps the opening turn and expected draw count",
                    List.of(0, Side.WHITE, example.awaitingBot(), example.draws()),
                    List.of(restored.ply(), restored.position().turn(), restored.awaitingBot(), draws.get()));
        }
    }

    private static MatchStore store(MemoryPreferences preferences) {
        return new MatchStore(preferences, () -> 1L, () -> "00000000-0000-4000-8000-000000000000");
    }

    @Test void resumesAfterTheComputersWhiteOpeningForABlackPlayer(Checks check) {
        var setup = new MatchSetup(Side.BLACK, Level.HARD, true);
        var before = ChessMatch.restore(ChessMatch.start(setup).checkpoint());
        var opening = before.chooseBotMove();
        check.same("The White opening is legal", true, before.position().legalMoves().contains(opening));
        var resumed = ChessMatch.restore(before.play(opening).checkpoint());
        check.same("The same match resumes on the human's Black turn", List.of(setup, 1, false, Side.BLACK),
                List.of(resumed.setup(), resumed.ply(), resumed.awaitingBot(), resumed.position().turn()));
        check.same("A legacy checkpoint still resumes as White with Basic", new MatchSetup(Side.WHITE, Level.BASIC, true),
                ChessMatch.restore("2|analysis|e2e4").setup());
        check.rejects("A malformed saved analysis option is rejected", IllegalArgumentException.class,
                () -> ChessMatch.restore("3|BLACK|HARD|yes|e2e4"));
    }

    @Test void randomizedOpponentIdentityIsChosenOnceAndSurvivesSaveAndResume(Checks check) {
        var draws = new AtomicInteger();
        var setup = MatchSetup.randomized(Side.WHITE, Level.HARD, true, bound -> {
            check.same("The random source receives the bounded roster size", MatchOpponentName.choices().size(), bound);
            draws.incrementAndGet();
            return 5;
        });
        check.same("A new match receives the selected random identity", "Nika", setup.opponentName());
        check.same("Basic, Advanced and Hard can share the same independent name roster", true,
                MatchOpponentName.choices().contains(setup.opponentName()));
        var match = ChessMatch.start(setup, 7).play(Move.fromUci("e2e4"));
        var restored = ChessMatch.restore(match.checkpoint());
        check.same("The named checkpoint keeps identity and opening choice with the match", List.of("Nika", 1, true, 7L),
                List.of(restored.setup().opponentName(), restored.ply(), restored.analysisEnabled(), restored.openingChoice()));
        check.same("Changing analysis does not regenerate the match identity", "Nika", restored.withAnalysis(false).setup().opponentName());

        var preferences = new MemoryPreferences();
        var store = store(preferences);
        check.same("The named match is committed to the saved-games slot", true,
                store.startMatch(1, match, false, MatchStore.GameOrigin.AI, false, false));
        var reopened = store(preferences);
        var saved = reopened.readSavedMatch(1);
        var row = MatchListEntry.saved(1, saved, reopened.startedAt(1));
        check.same("Saved list, resumed game and persisted checkpoint use the same name", List.of("Nika", "Nika", match.checkpoint()),
                List.of(row.opponentName(), saved.setup().opponentName(), saved.checkpoint()));
        var finished = ChessMatch.start(setup, 7).play(Move.fromUci("f2f3")).play(Move.fromUci("e7e5"))
                .play(Move.fromUci("g2g4")).play(Move.fromUci("d8h4"));
        check.same("The completed match is archived without losing the chosen name", true,
                reopened.saveMatch(1, finished, false, MatchStore.GameOrigin.AI, false, false));
        var archive = reopened.archivedMatches().getFirst();
        check.same("Finished list, archived checkpoint and reopened archive retain the identity", List.of("Nika", "Nika", "Nika"),
                List.of(MatchListEntry.finished(archive).opponentName(), archive.match().setup().opponentName(),
                        store(preferences).readArchivedMatch(archive.id()).match().setup().opponentName()));
        check.same("One identity is drawn at match creation only", 1, draws.get());
        check.rejects("A checkpoint cannot inject an invalid opponent identity", IllegalArgumentException.class,
                () -> ChessMatch.restore("6|WHITE|HARD|0|Bad_Name|"));
    }

    @Test void theThreeLevelsReachDifferentCompletedHorizonsOnTheSamePosition(Checks check) {
        var position = ChessPosition.fromFen("4k3/8/8/7p/P7/8/8/4K3 w - - 0 1");
        var depths = java.util.Arrays.stream(Level.values())
                .map(level -> TacticalAnalysis.botAnalysis(position, level).depth()).toList();
        check.same("In a quiet position Basic completes two plies and Advanced completes four", List.of(2, 4), depths.subList(0, 2));
        check.same("Hard completes the new sixth horizon on this position", 6, depths.get(2));
    }

    @Test void hardFollowsDocumentedTheoryAndKeepsItsIndependentSearchControl(Checks check) {
        var catalog = OpeningCatalog.load();
        for (String id : List.of("french", "scandinavian")) {
            var lesson = catalog.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
            boolean french = id.equals("french");
            var position = lesson.positionAt(french ? 9 : 7);
            var match = ChessMatch.fromPosition(position, new MatchSetup(Side.WHITE, Level.HARD, false));
            var decision = match.botDecision();
            check.same(id + " follows its documented opening continuation", french ? "d8b6" : "g8f6", decision.move().uci());
            check.same(id + " theory agrees with the actual position", true,
                    OpeningTheory.CURRENT.choices(position).stream().anyMatch(path -> path.move().equals(decision.move())));
            check.same(id + " uses at most the Hard budget", true,
                    decision.extraNodes() <= HardAdaptivePolicy.CURRENT.search().extraNodes());
            // An explicit nonzero comparison gap bypasses the book and preserves the independent search witness.
            var searched = TacticalAnalysis.botAnalysis(position, Level.HARD, 1);
            check.same(id + " retains the off-book horizon confirmation", french ? "c5d4" : "g8f6",
                    searched.result().ranked().getFirst().move().uci());
            check.same(id + " independent search keeps the configured ceiling", true,
                    searched.extraNodes() <= HardAdaptivePolicy.CURRENT.search().extraNodes());
        }
    }
}
