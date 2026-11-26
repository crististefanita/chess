package ro.atf.chess;
import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class StudyFenContractTest {
 private static final String START="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
 private static List<ChessPosition.Move> moves(String text){return java.util.Arrays.stream(text.split(" ")).map(ChessPosition.Move::fromUci).toList();}
 @Test void standardFenKeepsAnUncapturableDoublePushTarget(Checks check){
  var history=moves("c2c4");String fen=StudyFen.replay(START,history);
  check.same("Standard FEN retains c3 even though Black cannot capture en passant", "rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR b KQkq c3 0 1",fen);
  var board=ChessPosition.fromFen(fen);
  check.same("A recorded target never invents a legal en-passant capture",true,board.legalMoves().stream().noneMatch(m->m.to()==BoardSquare.parse("c3")));
 }
 @Test void realEnPassantAndItsClockArePreserved(Checks check){
  String fen=StudyFen.replay(START,moves("e2e4 a7a6 e4e5 d7d5"));
  check.same("The independent actual history preserves all six FEN fields", "rnbqkbnr/1pp1pppp/p7/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3",fen);
  check.same("The double push allows the actual pawn capture",true,ChessPosition.fromFen(fen).legalMoves().contains(ChessPosition.Move.fromUci("e5d6")));
  check.same("A subsequent quiet move clears the target and advances only the quiet clock", "- 1 3",String.join(" ",java.util.Arrays.copyOfRange(StudyFen.replay(START,moves("e2e4 a7a6 e4e5 d7d5 g1f3")).split(" "),3,6)));
 }
 @Test void aPinnedCaptureDoesNotEraseTheSourceTarget(Checks check){
  String fen="k3r3/8/8/3pP3/8/8/8/4K3 w - d6 0 1";
  check.same("Evidence preserves the composed origin's target without claiming that the capture is legal",fen,StudyFen.replay(fen,List.of()));
  check.same("A pawn pinned to its king cannot capture en passant",false,ChessPosition.fromFen(fen).legalMoves().contains(ChessPosition.Move.fromUci("e5d6")));
 }
}
