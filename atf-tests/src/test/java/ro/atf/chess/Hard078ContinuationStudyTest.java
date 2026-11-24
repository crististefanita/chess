package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@AtfComponentTest
@Tag("benchmark")
@ExtendWith(ExtentAudit.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfSystemProperty(named="chess.master.continuations", matches=".+")
class Hard078ContinuationStudyTest {
    private static final String INITIAL="2r5/1b4k1/2q1p1pr/2p5/1p1pR1QP/pP1P1PP1/P1P5/4R1K1 w - - 1 31";
    private record Case(String id,String prefix,String approved) { }
    private static final List<Case> CASES=List.of(
            new Case("after-Kf8","e4e6 c6f3 e6e7 g7f8","e1f1"),
            new Case("after-Kh8","e4e6 c6f3 e6e7 g7h8","e7e8"));
    private static final String HEADER="id\tinitialFen\tprefix\tfen\tpolicy\tpolicyId\tquietTactics\tmove\tscore\tbound\tdepth\tnodes\tobservedNodes\tcertificateNodes\tcertificateGenerated\tstop\tcertificate\tpv\tknownApproved\tknownOracleClass";
    private static Path measuredOut;
    private static List<String> measuredRows;
    private static Path output(){return Path.of(System.getProperty("chess.master.continuations")).toAbsolutePath().normalize();}

    @Test @Order(1)
    void bothFreeKingDefensesUseTheOneActualConfiguredPlayCalculation(Checks check) throws Exception {
        if(System.getProperty("chess.master.quietMode")!=null)throw new IllegalArgumentException("This study forbids a quiet-mode override");
        var out=output();Files.createDirectories(out);var policy=HardMasterPolicy.next();
        Files.writeString(out.resolve("plan.txt"),"Actual HardMaster.decideObserved(game, nextPolicy, false), exactly one calculation per context.\n"
                +"policy="+policy+"\nIsolated composed origin followed by the complete four-ply prefix; no pre-FEN repetition claim.\n"
                +"Known roots were independently confirmed with Stockfish H17/H19: Kf8 -> Rf1; Kh8 -> Re8.\n"
                +"A different root is NEEDS_ORACLE, not automatically incorrect. Receipt PASS is not the optional quality gate.\n",
                StandardOpenOption.CREATE_NEW);
        var rows=new ArrayList<String>();rows.add(HEADER);
        Files.write(out.resolve("decisions.tsv"),rows,StandardOpenOption.CREATE_NEW);
        for(var example:CASES){
            var game=ChessMatch.fromPosition(ChessPosition.fromFen(INITIAL));
            for(String step:example.prefix().split(" "))game=game.play(ChessPosition.Move.fromUci(step));
            check.same(example.id()+" actual context retains all four legal prefix moves",4,game.ply());
            var fen=StudyFen.replay(INITIAL,game.moveHistory());
            HardMaster.ObservedDecision observed;long counted;
            try(var scope=new HardSearchExperiment(HardSearchExperiment.Bonuses.NONE,p->0,
                    policy.maximumDecisionNodes(),policy.limits().tableEntries())){
                observed=HardMaster.decideObserved(game,policy,false);counted=scope.nodes;
            }
            var report=observed.report();check.same(example.id()+" no-book play retains its actual report",true,report!=null);
            var result=report.result();var decision=observed.decision();
            String move=decision==null?"-":decision.move().uci();
            boolean approved=example.approved().equals(move);
            String known=decision==null?"UNAVAILABLE":approved?"APPROVED_ROOT":"NEEDS_ORACLE";
            long certificateNodes=report.certificate()==null?0:report.certificate().nodes();
            long generated=report.certificate()==null?0:report.certificate().generated();
            String pv=result.line().stream().map(ChessPosition.Move::uci).collect(java.util.stream.Collectors.joining(" "));
            String row=example.id()+"\t"+INITIAL+"\t"+example.prefix()+"\t"+fen+"\t"+policy+"\t"+policy.id()+"\t"+policy.quietTactics()
                    +"\t"+move+"\t"+result.score()+"\t"+result.bound()+"\t"+result.completedDepth()+"\t"+result.nodes()+"\t"+counted
                    +"\t"+certificateNodes+"\t"+generated+"\t"+result.stop()+"\t"+report.certificateReceipt()+"\t"+pv+"\t"+approved+"\t"+known;
            rows.add(row);Files.writeString(out.resolve("decisions.tsv"),row+"\n",StandardOpenOption.APPEND);
            check.same(example.id()+" all core and certificate visits are charged",counted,result.nodes());
            check.same(example.id()+" no hidden allowance exceeds the configured complete cap",true,
                    result.nodes()>=0&&result.nodes()<=policy.maximumDecisionNodes());
            check.same(example.id()+" certificate and remaining core work have their declared allowances",true,
                    certificateNodes>=0&&certificateNodes<=policy.certificate().nodes()
                    &&result.nodes()>=certificateNodes&&result.nodes()-certificateNodes<=policy.limits().nodeLimit());
            if(decision==null){
                check.same(example.id()+" an incomplete search cannot produce a final score",null,result.score());
                check.same(example.id()+" an incomplete search cannot invent an executable PV",List.of(),result.line());
                check.same(example.id()+" no accepted iteration is concealed",false,result.hasCompletedIteration());
            }else{
                check.same(example.id()+" action and real PV select the same root",result.line().get(0),decision.move());
                check.same(example.id()+" playable root has an exact completed iteration",HardCandidateSearch.Bound.EXACT,result.bound());
                check.same(example.id()+" action retains the configured policy",policy.id(),decision.engine());
                check.same(example.id()+" action keeps total node accounting",ChessMatch.NodeAccounting.TOTAL,decision.accounting());
                check.same(example.id()+" action and PV keep the same depth",result.completedDepth(),decision.depth());
                check.same(example.id()+" action charges the same total work",result.nodes(),(long)decision.searchedNodes());
                check.same(example.id()+" action retains its actual certificate",report.certificateReceipt(),decision.certificate());
                check.same(example.id()+" stop reason comes from this calculation",result.stop().name(),decision.stop());
            }
            var board=game.position();for(var step:result.line()){
                check.same(example.id()+" every captured PV step is legal in its exact continuation",true,board.legalMoves().contains(step));
                board=board.play(step);
            }
        }
        check.same("Both independent king replies have actual continuation receipts",3,rows.size());
        measuredOut=out;measuredRows=List.copyOf(rows);
    }

    @Test @Order(2)
    @EnabledIfSystemProperty(named="chess.master.continuationQualityGate",matches="true")
    void knownApprovedRootsAreTheOptionalQualityGateNotAFabricatedEquivalenceClaim(Checks check) throws Exception {
        if(measuredRows==null||!output().equals(measuredOut))throw new IllegalStateException("Quality requires the actual measurement in this same JVM run");
        var rows=Files.readAllLines(output().resolve("decisions.tsv"));
        check.same("The quality gate cannot substitute another phase or edited receipt",measuredRows,rows);
        var pending=new ArrayList<String>();
        for(int index=0;index<CASES.size();index++){
            var expected=CASES.get(index);var fields=rows.get(index+1).split("\t",-1);
            check.same("Every row preserves the actual continuation schema",20,fields.length);
            check.same("Every row belongs to its declared king reply",expected.id(),fields[0]);
            check.same("The root keeps its complete prefix",expected.prefix(),fields[2]);
            check.same("The current configured policy, not a search override, produced this row",HardMasterPolicy.next().toString(),fields[4]);
            if(!expected.approved().equals(fields[7]))pending.add(expected.id()+":"+fields[7]+":"+fields[19]);
        }
        Files.writeString(output().resolve("quality-verdict.txt"),"Pending independent evidence="+pending+"\nA new root may be equivalent; NEEDS_ORACLE is not proof of a blunder.\n",StandardOpenOption.CREATE_NEW);
        check.same("Known approved continuations must be preserved; new alternatives need independent oracle evidence",List.of(),pending);
    }
}