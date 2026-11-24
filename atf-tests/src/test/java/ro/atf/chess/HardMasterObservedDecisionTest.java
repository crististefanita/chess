package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardMasterObservedDecisionTest {
    @Test void aBookObservationHasNoInventedSearchAndKeepsDecisionParity(Checks check) {
        var game = ChessMatch.start(); var policy = HardMasterPolicy.CURRENT;
        try (var scope = new HardSearchExperiment(HardSearchExperiment.Bonuses.NONE, p -> 0, policy.maximumDecisionNodes())) {
            var observed = HardMaster.decideObserved(game, policy);
            check.same("A repertoire observation explicitly identifies BOOK", "BOOK", observed.decision().stop());
            check.same("An unexecuted search has no report", null, observed.report());
            check.same("No search visits are invented for the repertoire", 0L, scope.nodes);
            check.same("The ordinary play adapter preserves the exact book action", observed.decision(), HardMaster.decide(game, policy));
        }
    }

    @Test void ordinaryAndCertifiedActionsRetainTheirOneActualCalculation(Checks check) {
        var ordinary = new HardMasterPolicy(HardMasterPolicy.Engine.A, "hard-a-v1",
                new HardCandidateSearch.Limits(3, 4, 20_000, 128), 4);
        var policies = List.of(ordinary, HardMasterPolicy.CURRENT);
        var fens = List.of("6k1/8/8/8/8/8/R7/6K1 w - - 0 1", "7k/5K2/6Q1/8/8/8/8/8 w - - 0 1");
        for (int index = 0; index < policies.size(); index++) {
            var policy = policies.get(index); var game = ChessMatch.fromPosition(ChessPosition.fromFen(fens.get(index)));
            HardMaster.ObservedDecision observed;
            try (var scope = new HardSearchExperiment(HardSearchExperiment.Bonuses.NONE, p -> 0, policy.maximumDecisionNodes())) {
                observed = HardMaster.decideObserved(game, policy, false);
                check.same("The observation charges the one actual complete calculation", scope.nodes, observed.report().result().nodes());
            }
            var report = observed.report(); var decision = observed.decision();
            check.same("The non-book action retains its real report", true, report != null && decision != null);
            check.same("The executable root comes from the captured PV", report.result().line().getFirst(), decision.move());
            check.same("The executable horizon comes from the same search", report.result().completedDepth(), decision.depth());
            check.same("The executable cost includes certificate and core", report.result().nodes(), (long) decision.searchedNodes());
            check.same("The executable certificate is the captured certificate", report.certificateReceipt(), decision.certificate());
            check.same("Direct play preserves the observed move and metadata", decision, HardMaster.decide(game, policy, false));
            check.same("A direct search independently reproduces this report", report.result(), HardMaster.search(game, policy, policy::evaluate, false).result());
            check.same("Both the ordinary core and a proved-mate receipt are exercised", index == 1,
                    report.result().stop() == HardCandidateSearch.Stop.MATE_CERTIFICATE);
        }
    }

    @Test void anIncompleteObservationKeepsEvidenceButCannotBePlayed(Checks check) {
        var game = ChessMatch.fromPosition(ChessPosition.fromFen("6k1/8/3p4/4r3/4Q3/8/8/6K1 w - - 0 1"));
        var policy = new HardMasterPolicy(HardMasterPolicy.Engine.A, "hard-a-v3",
                new HardCandidateSearch.Limits(3, 1, 20_000, 1_024), 1);
        var observed = HardMaster.decideObserved(game, policy, false);
        check.same("The unresolved calculation is still observable", HardCandidateSearch.Stop.QUIESCENCE_LIMIT, observed.report().result().stop());
        check.same("No action is fabricated from an incomplete proof", null, observed.decision());
        check.same("No numeric score is fabricated", null, observed.report().result().score());
        check.same("Unsuccessful work remains recorded", true, observed.report().result().nodes() > 0);
        check.rejects("The public play adapter still rejects the same incomplete proof", IllegalStateException.class,
                () -> HardMaster.decide(game, policy, false));
    }

    @Test void cancellationAndEndedGamesKeepTheExistingFailureContract(Checks check) {
        var game = ChessMatch.start(); var policy = HardMasterPolicy.CURRENT;
        try {
            Thread.currentThread().interrupt();
            check.rejects("An observation cannot hide cancellation behind a book action", CancellationException.class,
                    () -> HardMaster.decideObserved(game, policy));
            check.rejects("The ordinary adapter preserves cancellation", CancellationException.class,
                    () -> HardMaster.decide(game, policy));
        } finally { Thread.interrupted(); }
        var ended = ChessMatch.fromPosition(ChessPosition.fromFen("7k/8/8/8/8/8/8/K7 w - - 0 1"));
        check.rejects("An observation cannot reopen an ended game", IllegalStateException.class,
                () -> HardMaster.decideObserved(ended, policy));
    }

    @Test void aMissingReportRequiresADeclaredBookAction(Checks check) {
        check.rejects("An empty observation is not a book receipt", IllegalArgumentException.class,
                () -> new HardMaster.ObservedDecision(null, null));
        var move = ChessPosition.Move.fromUci("e2e4");
        check.rejects("A legacy action cannot pretend to be an unsearched book action", IllegalArgumentException.class,
                () -> new HardMaster.ObservedDecision(new ChessMatch.BotDecision(move, 0, 0), null));
        var book = new ChessMatch.BotDecision(move, 0, 0, ChessMatch.NodeAccounting.TOTAL, "hard-a-v2", "BOOK");
        var report = new HardMaster.Report(new HardCandidateSearch.Result(0, HardCandidateSearch.Bound.EXACT,
                List.of(move), 1, 1, 0, HardCandidateSearch.Stop.COMPLETED), null);
        check.rejects("A declared book action cannot attach an unrelated search", IllegalArgumentException.class,
                () -> new HardMaster.ObservedDecision(book, report));
    }
    @Test void substitutedActionsAndIncompleteReportsCannotBecomeAuditedPlay(Checks check) {
        var move = ChessPosition.Move.fromUci("e2e4");
        var result = new HardCandidateSearch.Result(10, HardCandidateSearch.Bound.EXACT,
                List.of(move), 1, 100, 0, HardCandidateSearch.Stop.COMPLETED);
        var report = new HardMaster.Report(result, null);
        var good = new ChessMatch.BotDecision(move, 1, 100, ChessMatch.NodeAccounting.TOTAL, "hard-a-v6", "COMPLETED");
        check.same("A matching synthetic action/report can establish the receipt invariant", good,
                new HardMaster.ObservedDecision(good, report).decision());
        for (var altered : List.of(
                new ChessMatch.BotDecision(ChessPosition.Move.fromUci("d2d4"), 1, 100, ChessMatch.NodeAccounting.TOTAL, "hard-a-v6", "COMPLETED"),
                new ChessMatch.BotDecision(move, 2, 100, ChessMatch.NodeAccounting.TOTAL, "hard-a-v6", "COMPLETED"),
                new ChessMatch.BotDecision(move, 1, 99, ChessMatch.NodeAccounting.TOTAL, "hard-a-v6", "COMPLETED"),
                new ChessMatch.BotDecision(move, 1, 100, ChessMatch.NodeAccounting.LEGACY_EXTRA, "hard-a-v6", "COMPLETED"),
                new ChessMatch.BotDecision(move, 1, 100, ChessMatch.NodeAccounting.TOTAL, "hard-a-v6", "NODE_LIMIT"),
                new ChessMatch.BotDecision(move, 1, 100, ChessMatch.NodeAccounting.TOTAL, "hard-a-v6", "COMPLETED", "PROVED:nodes=1:generated=1"))) {
            check.rejects("Root, horizon, cost, accounting, stop and certificate substitutions are rejected", IllegalArgumentException.class,
                    () -> new HardMaster.ObservedDecision(altered, report));
        }
        var incomplete = new HardMaster.Report(new HardCandidateSearch.Result(null, null, List.of(), 0, 100, 0,
                HardCandidateSearch.Stop.QUIESCENCE_LIMIT), null);
        check.same("An incomplete observation retains evidence without inventing an action", null,
                new HardMaster.ObservedDecision(null, incomplete).decision());
        check.rejects("An incomplete report cannot support a playable action", IllegalArgumentException.class,
                () -> new HardMaster.ObservedDecision(good, incomplete));
        check.rejects("A complete playable report cannot silently omit its action", IllegalArgumentException.class,
                () -> new HardMaster.ObservedDecision(null, report));
        var bounded = new HardMaster.Report(new HardCandidateSearch.Result(10, HardCandidateSearch.Bound.LOWER,
                List.of(move), 1, 100, 0, HardCandidateSearch.Stop.COMPLETED), null);
        check.rejects("A numeric bound is not a completed exact decision", IllegalArgumentException.class,
                () -> new HardMaster.ObservedDecision(good, bounded));
        var cancelled = new HardMaster.Report(new HardCandidateSearch.Result(10, HardCandidateSearch.Bound.EXACT,
                List.of(move), 1, 100, 0, HardCandidateSearch.Stop.CANCELLED), null);
        var cancelledAction = new ChessMatch.BotDecision(move, 1, 100, ChessMatch.NodeAccounting.TOTAL, "hard-a-v6", "CANCELLED");
        check.rejects("Even matching metadata cannot make a cancelled calculation playable", IllegalArgumentException.class,
                () -> new HardMaster.ObservedDecision(cancelledAction, cancelled));
        var fakeBook = new ChessMatch.BotDecision(move, 0, 1, ChessMatch.NodeAccounting.TOTAL, "hard-a-v6", "BOOK");
        check.rejects("A missing report cannot conceal billed book work", IllegalArgumentException.class,
                () -> new HardMaster.ObservedDecision(fakeBook, null));
    }}