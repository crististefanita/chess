package ro.atf.chess;
import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardCandidatePinnedQueenBatteryTest {
    private static final String FEN="2r5/1b4k1/2q1p1pr/2p5/1p1pR1QP/pP1P1PP1/P1P5/4R1K1 w - - 1 31";
    private static ChessPosition actual() {
        var p=ChessPosition.fromFen(FEN);
        for(String step:"e4e6 c6f3 e6e7 g7h8 e7e8 c8e8 e1e8 h8g7 g4d7 f3f7".split(" "))p=p.play(Move.fromUci(step));
        return p;
    }
    private static int material(ChessPosition p) {
        int sum=0;
        for(int square=0;square<64;square++) {
            var piece=p.pieceAt(square);if(piece==null)continue;
            int value=switch(piece.kind()){case QUEEN->900;case ROOK->500;case BISHOP,KNIGHT->300;case PAWN->100;case KING->0;};
            sum+=piece.side()==p.turn()?value:-value;
        }
        return sum;
    }
    private static HardCandidateSearch engine(int mode) {
        return new HardCandidateSearch(HardCandidatePinnedQueenBatteryTest::material,
                HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY,false,true,true,true,true,0,mode);
    }
    private static int minimax(ChessPosition p,int left,int ply) {
        var legal=p.legalMoves();if(legal.isEmpty())return p.isCheck()?-HardCandidateSearch.MATE+ply:0;
        if(ChessMatch.fromPosition(p).result()==ChessMatch.Result.MATERIAL_DRAW)return 0;
        if(left==0)return material(p);
        int best=-HardCandidateSearch.LIMIT;
        for(var move:legal)best=Math.max(best,-minimax(p.play(move),left-1,ply+1));
        return best;
    }
    @Test void theRealBatteryAddsANewLegalAttackWithoutRequiringANewPin(Checks check) {
        var before=actual();var move=Move.fromUci("e8e7");int queen=BoardSquare.parse("f7");
        check.same("The queen is already absolutely pinned before the quiet move",true,
                PawnLeverPolicy.queenPinnedToKing(before,before.turn().other()));
        check.same("The move is legal in its full tactical context",true,before.legalMoves().contains(move));
        check.same("The rook did not attack that queen before moving",false,before.reachesFrom(move.from(),queen));
        var after=before.play(move);
        check.same("Re7 is not check because Qf7 blocks the rook",false,after.isCheck());
        check.same("The queen remains pinned, with the rook replacing the queen's pinning ray",true,
                PawnLeverPolicy.queenPinnedToKing(after));
        check.same("The moved rook now attacks the pinned queen",true,after.reachesFrom(move.to(),queen));
        check.same("Mode six rejects a pre-existing pin",false,engine(6).eligibleQuietPinThreat(before,move));
        check.same("Mode seven admits exactly the newly added legal attack",true,engine(7).eligibleQuietPinThreat(before,move));
        check.same("The defender may capture the attacking rook",true,after.legalMoves().contains(Move.fromUci("f7e7")));
        check.same("King moves remain unrestricted legal defenses",true,after.legalMoves().contains(Move.fromUci("g7g8")));
        check.same("A pawn intermezzo is not removed from the free response set",true,after.legalMoves().contains(Move.fromUci("c5c4")));
    }
    @Test void anUnusedQuietMoveOrAnOffLineKingDoesNotTriggerThePattern(Checks check) {
        var before=actual();
        check.same("An unrelated king move does not gain a tactical extension",false,
                engine(7).eligibleQuietPinThreat(before,Move.fromUci("g1h2")));
        check.same("An unrelated legal rook move does not qualify merely because the pin persists",false,
                engine(7).eligibleQuietPinThreat(before,Move.fromUci("e8e6")));
        var noPin=ChessPosition.fromFen("4R3/3Q1q2/7k/8/8/8/8/6K1 w - - 0 1");
        check.same("The king off the queen's ray invalidates the battery predicate",false,
                PawnLeverPolicy.addsAttackOnPinnedQueen(noPin,Move.fromUci("e8e7")));
        var oldAttack=ChessPosition.fromFen("8/3Q1qk1/8/8/8/8/8/5RK1 w - - 0 1");
        check.same("Moving a rook which already attacked the queen is not a new attack",false,
                PawnLeverPolicy.addsAttackOnPinnedQueen(oldAttack,Move.fromUci("f1f2")));
    }
    @Test void theFreeReplyProofPreservesTheIndependentBatteryGainInBothColors(Checks check) {
        for(String fen:List.of("4R3/3Q1qk1/8/8/8/8/8/6K1 w - - 0 1",
                "6k1/8/8/8/8/8/3q1QK1/4r3 b - - 0 1")) {
            var context=HardCandidateSearch.Context.fromFen(fen);
            int independent=minimax(context.position(),3,0);
            check.same("Exhaustive three-ply minimax verifies a material gain without prescribed replies",true,independent>=900);
            var result=engine(7).fixed(context,0,new HardCandidateSearch.Limits(1,16,1_000_000,4_096),
                    -HardCandidateSearch.LIMIT,HardCandidateSearch.LIMIT);
            check.same("The bounded forcing proof completes",true,result.score()!=null);
            check.same("Free legal reply search preserves at least the independently proved gain",true,result.score()>=independent);
            check.same("Every generated proof visit is still charged to the same cap",true,result.nodes()<=1_000_000);
            var p=context.position();for(var move:result.line()){check.same("Every returned battery step is legal",true,p.legalMoves().contains(move));p=p.play(move);}
        }
    }
    @Test void anInterruptedBatteryCannotEscapeAsAStandPatScore(Checks check) {
        var context=HardCandidateSearch.Context.fromFen("4R3/3Q1qk1/8/8/8/8/8/6K1 w - - 0 1");
        var result=engine(7).fixed(context,0,new HardCandidateSearch.Limits(1,16,1,0),
                -HardCandidateSearch.LIMIT,HardCandidateSearch.LIMIT);
        check.same("The added attack must pay for its child",HardCandidateSearch.Stop.NODE_LIMIT,result.stop());
        check.same("No final score is fabricated",null,result.score());
        check.same("No executable proof line is fabricated",List.of(),result.line());
    }
}