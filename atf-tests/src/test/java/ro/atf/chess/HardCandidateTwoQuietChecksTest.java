package ro.atf.chess;
import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardCandidateTwoQuietChecksTest {
    private static HardCandidateSearch engine(int mode) {
        return new HardCandidateSearch(p -> 0, HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY,
                false, true, true, true, true, 0, mode);
    }
    private static int minimax(ChessPosition p, int left, int ply) {
        var legal=p.legalMoves();
        if(legal.isEmpty())return p.isCheck()?-HardCandidateSearch.MATE+ply:0;
        if(left==0)return 0;
        int best=-HardCandidateSearch.LIMIT;
        for(var move:legal)best=Math.max(best,-minimax(p.play(move),left-1,ply+1));
        return best;
    }
    @Test void twoQuietChecksReachTheSameMateAsExhaustiveThreePlyMinimax(Checks check) {
        for(String fen:List.of("8/3Q2pp/6k1/3K4/6P1/8/7P/q7 w - - 10 47",
                "Q7/7p/8/6p1/3k4/6K1/3q2PP/8 b - - 10 47")) {
            var context=HardCandidateSearch.Context.fromFen(fen);
            int exhaustive=minimax(context.position(),3,0);
            check.same("Independent legal minimax verifies mate in two, not a canned engine solution",HardCandidateSearch.MATE-3,exhaustive);
            var limits=new HardCandidateSearch.Limits(1,16,1_000_000,4_096);
            var one=engine(5).fixed(context,0,limits,-HardCandidateSearch.LIMIT,HardCandidateSearch.LIMIT);
            var two=engine(6).fixed(context,0,limits,-HardCandidateSearch.LIMIT,HardCandidateSearch.LIMIT);
            check.same("One quiet check cannot certify the two-check mate",true,one.score()==null||one.score()<exhaustive);
            check.same("Two quiet checks reproduce the independent full legal proof",exhaustive,two.score());
            check.same("The finite proof completes within the original cap",true,two.nodes()<=limits.nodeLimit());
            var end=context.position();for(var step:two.line())end=end.play(step);
            check.same("The actual returned line ends in mate",true,end.isCheckmate());
        }
    }
    @Test void theLargerQuietCheckReserveDoesNotInventAnInterruptedScore(Checks check) {
        var context=HardCandidateSearch.Context.fromFen("8/3Q2pp/6k1/3K4/6P1/8/7P/q7 w - - 10 47");
        var tiny=engine(6).fixed(context,0,new HardCandidateSearch.Limits(1,16,1,0),-HardCandidateSearch.LIMIT,HardCandidateSearch.LIMIT);
        check.same("Every threat still pays for its child",HardCandidateSearch.Stop.NODE_LIMIT,tiny.stop());
        check.same("No score survives an unfinished proof",null,tiny.score());
    }
}