package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class NativeFrontierRegressionTest {
    @Test void aCorrectStoredRootDoesNotCertifyItsUnclosedPrincipalVariation(Checks check)throws Exception {
        var lesson=ChessYaml.resource("hard-lessons/correct-root-unclosed-line.yaml");
        check.same("The lesson is a stored counterexample, not a requirement to retain a bad live PV",1,ChessYaml.number(lesson,"schema"));
        int count=0;for(Object entry:(java.util.List<?>)lesson.get("cases")){
            @SuppressWarnings("unchecked") var row=(java.util.Map<String,Object>)entry;
            var board=ChessPosition.fromFen((String)row.get("initialFen"));
            for(Object value:(java.util.List<?>)row.get("history"))board=board.play(ChessPosition.Move.fromUci((String)value));
            var pv=(java.util.List<?>)row.get("storedNativePv");
            check.same("The stored line begins with the independently accepted root",row.get("acceptedRoot"),pv.getFirst());
            for(Object value:pv)board=board.play(ChessPosition.Move.fromUci((String)value));
            var capture=ChessPosition.Move.fromUci((String)row.get("visibleQueenRecapture"));
            check.same(row.get("id")+" the stored line still requires a legal reply to check",true,board.isCheck());
            check.same("The visible king recapture is legal in the complete position",true,board.legalMoves().contains(capture));
            check.same("The unclosed offer is a queen",ChessPosition.Kind.QUEEN,board.pieceAt(capture.to()).kind());
            check.same("The forced calculation can remove the queen, despite a correct first move",ChessPosition.Kind.KING,board.play(capture).pieceAt(capture.to()).kind());count++;
        }check.same("Both color-equivalent stored continuations retain the same counterexample",2,count);
    }
    @Test void realNativeDecisionsRetainTheTeacherConfirmedWinningControls(Checks check)throws Exception {
        int count=0;for(var witness:NativeFrontierWitnesses.load())if(witness.nativeGuard()){
            var game=witness.game();String before=game.checkpoint();var observed=game.analyzeBot(MatchSetup.Level.HARD);HardQuestAudit.validate(game,observed);
            check.same(witness.id()+" actual Native H"+observed.decision().depth()+" extra="+observed.decision().extraNodes(),witness.selected(),observed.decision().move().uci());
            for(int depth:new int[]{17,19}){
                check.same(witness.id()+" selected root preserves the teacher result at H"+depth,0.0,witness.loss(witness.selected(),depth));
                check.same(witness.id()+" rejected experimental root has independently captured WDL loss at H"+depth,true,witness.loss(witness.rejected(),depth)>150);
            }
            check.same("Analysis does not execute or replace the Native game move",before,game.checkpoint());count++;
        }check.same("The guard covers three tactics and both orientations of training-021",5,count);
    }
    @Test void capturedTeacherLinesAndUnsolvedGoalsKeepSeparateScopes(Checks check)throws Exception {
        var witnesses=NativeFrontierWitnesses.load();int phases=0,unsolved=0;for(var w:witnesses){phases+=w.input().path("phases").size();if(!w.nativeGuard())unsolved++;}
        check.same("Every H17/H19 free/forced phase is replayed and validated offline",42,phases);
        check.same("The two training-065 contexts are goals, not fabricated passing Native recoveries",2,unsolved);
    }
    @Test void foreignPositionHorizonAndChangedRawBestmoveAreRejected(Checks check)throws Exception {
        var first=NativeFrontierWitnesses.load().getFirst();
        var changed=first.input().deepCopy();((ObjectNode)changed.path("phases").get(0)).put("nativeFen","rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        check.rejects("A phase cannot borrow another board",IllegalArgumentException.class,()->NativeFrontierWitnesses.read(changed));
        var horizon=first.input().deepCopy();((ObjectNode)horizon.path("phases").get(0)).put("depth",19);
        check.rejects("A completed depth cannot be relabelled",IllegalArgumentException.class,()->NativeFrontierWitnesses.read(horizon));
        var raw=first.input().deepCopy();var lines=(com.fasterxml.jackson.databind.node.ArrayNode)raw.path("phases").get(0).path("raw");
        for(int i=0;i<lines.size();i++)if(lines.get(i).asText().startsWith("bestmove "))lines.set(i,LearningGameAudit.mapper().getNodeFactory().textNode("bestmove a1a1"));
        check.rejects("The original best move cannot be substituted",IllegalArgumentException.class,()->NativeFrontierWitnesses.read(raw));
    }
}
