package ro.atf.chess;
import atf.testkit.AtfComponentTest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class SearchKeyPackingTest {
    private static void compare(ChessPosition position) {
        long[] words=new long[4];
        // Frozen full-board packing oracle: no occupancy traversal and no production-key helper.
        for(int square=0;square<64;square++){
            var piece=position.pieceAt(square);
            int code=piece==null?0:1+piece.side().ordinal()*6+piece.kind().ordinal();
            words[square/16]|=(long)code<<((square%16)*4);
        }
        var actual=position.searchKey();
        if(!Arrays.equals(words,new long[]{actual.first(),actual.second(),actual.third(),actual.fourth()}))
            throw new AssertionError("The full lossless key differs from the 64-square oracle");
    }
    @Test void sparsePackingRetainsEveryNibbleAcrossCapturesAndLongWalks(Checks c) {
        var random=new Random(0x65a29ab3L);int visited=0;
        for(int game=0;game<24;game++){
            var p=ChessPosition.initial();
            for(int ply=0;ply<100;ply++){
                compare(p);visited++;var moves=p.legalMoves();if(moves.isEmpty())break;
                p=p.play(moves.get(random.nextInt(moves.size())));
            }
        }
        c.same("Over 1800 independent walk states retain the original full-board packing",true,visited>1800);
    }
    @Test void specialStateAndSparseEndgamesRemainLossless(Checks c) {
        for(String fen:List.of("7k/8/8/8/8/8/8/K7 w - - 0 1",
                "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
                "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1",
                "7k/P7/8/8/8/8/8/7K w - - 0 1")){
            var p=ChessPosition.fromFen(fen);compare(p);
            for(var move:p.legalMoves())compare(p.play(move));
        }
        var rights=ChessPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1").searchKey();
        var without=ChessPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1").searchKey();
        c.same("Identical pieces with different castling rights retain different keys",false,rights.equals(without));
        var ep=ChessPosition.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1").searchKey();
        var noEp=ChessPosition.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - - 0 1").searchKey();
        c.same("A legal en passant right remains part of the exact key",false,ep.equals(noEp));
    }
}
