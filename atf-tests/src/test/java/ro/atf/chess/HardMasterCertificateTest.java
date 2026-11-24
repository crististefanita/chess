package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardMasterCertificateTest {
    @Test void theRuntimeRecoversMatesWithoutReceivingCatalogKeys(Checks check) {
        for (String fen : List.of(
                "rn1qk2r/ppp2ppp/3p2b1/2bP4/2P1P1P1/P1N1p2P/1P2P1B1/R1BQK1NR b KQkq - 0 10",
                "Q7/8/8/5k2/8/5pr1/5r2/R6K b - - 0 50",
                "2r3k1/p4ppp/qp3b2/3Q4/5PN1/P5P1/1Pr4P/3RR1K1 w - - 3 28")) {
            var game = ChessMatch.fromPosition(ChessPosition.fromFen(fen));
            var report = HardMaster.search(game, HardMasterPolicy.CURRENT,
                    p -> TacticalAnalysis.positionValue(p, p.turn()), false);
            var result = report.result();
            check.same("The runtime selects an independently proved mate", HardCandidateSearch.Stop.MATE_CERTIFICATE, result.stop());
            check.same("An exact certificate supplies its representative longest defense", report.certificate().line(), result.line());
            check.same("No core visits are invented when a proof is sufficient", (long) report.certificate().nodes(), result.nodes());
            check.same("The certified distance is the actual line length", result.line().size(), result.completedDepth());
            var end = game;
            for (var move : result.line()) end = end.play(move);
            check.same("The captured representative defense ends in mate", true, end.position().isCheckmate());
            var decision = HardMaster.decide(game);
            check.same("The application publishes the proved root", result.line().getFirst(), decision.move());
            check.same("The application identifies the certified engine", "hard-a-v2", decision.engine());
            check.same("The proof's visits and generated boards remain auditable", report.certificateReceipt(), decision.certificate());
        }
    }

    @Test void anUnprovedCertificateCannotStealCoreNodesOrChangeTheChoice(Checks check) {
        var game = replay("a2a3 a7a6");
        var current = HardMasterPolicy.CURRENT;
        var baseline = new HardMasterPolicy(HardMasterPolicy.Engine.A, "hard-a-v1", current.limits(), current.forcingReserve());
        var core = HardMaster.analyze(game, baseline);
        for (var settings : List.of(new HardMasterPolicy.Certificate(5, 100_000), new HardMasterPolicy.Certificate(7, 1_000))) {
            var policy = new HardMasterPolicy(HardMasterPolicy.Engine.A, "hard-a-v2", current.limits(), current.forcingReserve(), settings);
            var report = HardMaster.search(game, policy, p -> TacticalAnalysis.positionValue(p, p.turn()), false);
            var expected = settings.maxPlies() == 7 ? HardMateCertificate.Status.BUDGET_EXHAUSTED : HardMateCertificate.Status.NO_MATE;
            check.same("Both a refuted proof and an exhausted proof are exercised", expected, report.certificate().status());
            check.same("An unproved search publishes no mating line", List.of(), report.certificate().line());
            check.same("The unchanged core keeps its move and full horizon", core.line(), report.result().line());
            check.same("The unchanged core keeps its score", core.score(), report.result().score());
            check.same("The unchanged core keeps its depth", core.completedDepth(), report.result().completedDepth());
            check.same("Both algorithms' actual visits are charged", core.nodes() + report.certificate().nodes(), report.result().nodes());
            check.same("An unproved certificate cannot turn into an exact mating claim", core.stop(), report.result().stop());
            if (expected == HardMateCertificate.Status.BUDGET_EXHAUSTED) check.same("Exhaustion consumes precisely its own allowance", 1_000, report.certificate().nodes());
        }
    }

    @Test void aBookHitDoesNotLaunchTheCertificateOrCore(Checks check) {
        var decision = HardMaster.decide(ChessMatch.start());
        check.same("The common repertoire remains the first path", "BOOK", decision.stop());
        check.same("Neither search ran for a book hit", 0, decision.searchedNodes());
        check.same("An unexecuted proof is explicitly identified", "NOT_RUN", decision.certificate());
    }
    private static ChessMatch replay(String moves) {
        var match = ChessMatch.start();
        for (String move : moves.split(" ")) match = match.play(ChessPosition.Move.fromUci(move));
        return match;
    }}
