package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardMasterRemoteCompatibilityTest {
    @Test void branchingAndGeneratedMovesPreserveTheRemoteOpeningIdentity(Checks check) {
        var setup = new MatchSetup(ChessPosition.Side.WHITE, MatchSetup.Level.HARD, true);
        var game = ChessMatch.start(setup, 42).play(ChessPosition.Move.fromUci("e2e4"));
        var move = ChessPosition.Move.fromUci("e7e5");
        var ordinary = game.play(move);
        var generated = game.afterGenerated(move, game.position().playGenerated(move));
        check.same("The explicit remote opening choice survives the search adapter", 42L, generated.openingChoice());
        check.same("Generated legal moves preserve the full serialized match", ordinary.checkpoint(), generated.checkpoint());
        check.same("The remote format five remains the checkpoint format", true, generated.checkpoint().startsWith("5|"));
        check.same("Restoring the checkpoint retains the same remote opening choice", 42L,
                ChessMatch.restore(generated.checkpoint()).openingChoice());
        check.same("History branches retain opening choice", 42L, generated.continueFrom(1).openingChoice());
        check.same("The A context retains the exact current board", generated.position().searchKey(), HardMaster.context(generated).position().searchKey());
    }

    @Test void theUnqualifiedExperimentalMasterCannotReplaceTheRemoteProfiles(Checks check) {
        check.same("The product engine remains the remote legacy route", false, HardMaster.usesA());
        var setup = new MatchSetup(ChessPosition.Side.BLACK, MatchSetup.Level.HARD, false);
        var game = ChessMatch.start(setup, 3);
        for (var level : MatchSetup.Level.values()) {
            var actual = game.botDecision(level);
            var nativeDecision = game.referenceDecision(level);
            check.same("The default level route is unchanged for " + level, nativeDecision, actual);
            check.same("Legacy node accounting is not mislabeled total for " + level,
                    ChessMatch.NodeAccounting.LEGACY_EXTRA, actual.accounting());
        }
    }
}