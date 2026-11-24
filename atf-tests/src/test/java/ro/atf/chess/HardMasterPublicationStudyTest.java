package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
@EnabledIfSystemProperty(named = "chess.master.publicationGate", matches = ".+")
class HardMasterPublicationStudyTest {
    @Test void everyMaterialGuardUsesTheActualPublishedEvaluatorAndPolicy(Checks check) throws Exception {
        var out = Path.of(System.getProperty("chess.master.publicationGate"));
        Files.createDirectories(out);
        var policy = HardMasterPolicy.next();
        int quietMode = Integer.getInteger("chess.master.quietMode", policy.quietTactics().mode());
        Files.writeString(out.resolve("plan.txt"), "Published checkout, not the old live evaluator.\npolicy=" + policy
                + "\nquietMode=" + quietMode + "; explicitOverride=" + (System.getProperty("chess.master.quietMode") != null)
                + "; isolated FEN fixtures, no pre-FEN repetition history\n"
                + "Approved roots were independently checked at Stockfish H17/H19; a new root needs a new oracle receipt.\n",
                StandardOpenOption.CREATE_NEW);
        var rows = new ArrayList<String>();
        rows.add("id\tfen\tquietMode\tmove\tdepth\tnodes\tscore\tbound\tstop\tapproved\tpv\tcertificate");
        var failed = new ArrayList<String>();
        var cases = ChessYaml.list(ChessYaml.resource("hard-master-material-regressions.yaml"), "cases");
        check.same("The complete fixed material gate has eleven guards", 11, cases.size());
        for (var item : cases) {
            var guard = (java.util.Map<?, ?>) item;
            String id = ChessYaml.string(guard, "id"), fen = ChessYaml.string(guard, "fen");
            var game = ChessMatch.fromPosition(ChessPosition.fromFen(fen));
            HardMaster.Report report;
            ChessMatch.BotDecision observedDecision = null;
            try (var scope = new HardSearchExperiment(HardSearchExperiment.Bonuses.NONE, p -> 0,
                    policy.maximumDecisionNodes(), policy.limits().tableEntries())) {
                if (System.getProperty("chess.master.quietMode") == null) {
                    var observed = HardMaster.decideObserved(game, policy, false);
                    report = observed.report(); observedDecision = observed.decision();
                    check.same(id + " a no-book observation always retains its actual search report", true, report != null);
                } else {
                    report = HardMaster.search(game, policy, policy::evaluate, false, policy.rootPv(), policy.pvs(),
                            policy.moveHints(), policy.cutoffOrdering(), 0, quietMode);
                }
                check.same(id + " includes all certificate and core visits", scope.nodes, report.result().nodes());
            }
            var result = report.result();
            String move = result.line().isEmpty() ? "-" : result.line().getFirst().uci();
            if (System.getProperty("chess.master.quietMode") == null) {
                check.same(id + " the played root is the actual observed action, not a second search",
                        move, observedDecision == null ? "-" : observedDecision.move().uci());
                if (observedDecision != null) {
                    check.same(id + " observed action retains the actual PV horizon", result.completedDepth(), observedDecision.depth());
                    check.same(id + " observed action charges the same complete calculation", result.nodes(), (long) observedDecision.searchedNodes());
                    check.same(id + " observed action retains the actual certificate", report.certificateReceipt(), observedDecision.certificate());
                }
            }
            boolean approved = ChessYaml.list(guard, "approved-roots").contains(move);
            if (!approved) failed.add(id + ":" + move + ":" + result.stop());
            if (!move.equals("-")) check.same(id + " selected root is legal", true,
                    game.position().legalMoves().contains(ChessPosition.Move.fromUci(move)));
            check.same(id + " charges a nonnegative cost within the declared complete cap", true,
                    result.nodes() >= 0 && result.nodes() <= policy.maximumDecisionNodes());
            String pv = result.line().stream().map(ChessPosition.Move::uci).collect(java.util.stream.Collectors.joining(" "));
            var board = game.position();
            for (var step : result.line()) {
                check.same(id + " actual PV is legal at each step", true, board.legalMoves().contains(step));
                board = board.play(step);
            }
            rows.add(id + "\t" + fen + "\t" + quietMode + "\t" + move + "\t" + result.completedDepth() + "\t"
                    + result.nodes() + "\t" + result.score() + "\t" + result.bound() + "\t" + result.stop() + "\t"
                    + approved + "\t" + pv + "\t" + report.certificateReceipt());
            Files.write(out.resolve("decisions.tsv"), rows);
            System.out.println("Publication material guard " + id + " root=" + move + " approved=" + approved);
        }
        Files.writeString(out.resolve("verdict.txt"), "accepted=" + (cases.size() - failed.size()) + "/" + cases.size()
                + "\nfailedRoots=" + failed + "\nNo product promotion or Elo inference.\n", StandardOpenOption.CREATE_NEW);
        check.same("All eleven actual decisions have receipts", 12, rows.size());
        check.same("Zero previously recovered material guards may regress; unapproved roots need independent oracle", List.of(), failed);
    }
}