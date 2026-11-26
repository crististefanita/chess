package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** Pure import contracts. Synthetic fixtures do not represent an engine or an emulator execution. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AndroidStockfishSanityEvidenceTest {
    private static final String RUN = "1234567890abcdef1234567890abcdef";
    @TempDir Path folder;

    @Test void nativeImportRequiresExactlyTheOriginal37OrderedAssertionsAndRun(Checks c) {
        String valid = fixture();
        var receipt = AndroidStockfishSanityEvidence.readNative(valid, RUN);
        c.same("The immutable phase inventory contains 37 original assertions", 37, receipt.rows().size());
        c.same("Display values and original verdicts are imported without new equality comparisons", "actual-value-1", receipt.rows().get(0).actual());
        c.same("CRLF native output retains the same 37-row contract", 37,
                AndroidStockfishSanityEvidence.readNative(valid.replace("\n", "\r\n"), RUN).rows().size());
        for (var bad : List.of(
                new BadReceipt("missing assertion 07", valid.replaceAll("(?m)^INSTRUMENTATION_RESULT: check_07=.*\\R", "")),
                new BadReceipt("foreign run ID", valid.replace(RUN, "ffffffffffffffffffffffffffffffff")),
                new BadReceipt("duplicate assertion 06", valid.replace("check_07=", "check_06=")),
                new BadReceipt("substituted assertion name", valid.replace("preserves the complete history and clocks", "launched")),
                new BadReceipt("original failed verdict", valid.replace("\ttrue", "\tfalse")),
                new BadReceipt("failed instrumentation result", valid.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0")),
                new BadReceipt("duplicate PASS marker", valid + "INSTRUMENTATION_RESULT: result=PASS\n"),
                new BadReceipt("no assertion evidence", "INSTRUMENTATION_RESULT: result=PASS\nINSTRUMENTATION_CODE: -1\n"))) {
            c.same("Negative fixture changes the original bytes: " + bad.name(), false, valid.equals(bad.content()));
            c.rejects("Reject native evidence: " + bad.name(), IllegalArgumentException.class,
                    () -> AndroidStockfishSanityEvidence.readNative(bad.content(), RUN));
        }
        c.rejects("A failed verdict stays failed with CRLF line endings", IllegalArgumentException.class,
                () -> AndroidStockfishSanityEvidence.readNative(valid.replace("\n", "\r\n").replace("\ttrue", "\tfalse"), RUN));
    }

    @Test void screenshotMustBeTheActualPngBytesBoundByTheNativeMarker(Checks c) throws Exception {
        byte[] original = png(0xff57e0b4); String expected = AndroidStockfishSanityEvidence.sha(original);
        AndroidStockfishSanityEvidence.validatePng(original, expected);
        c.same("A synthetic PNG fixture is readable and hash-bound", 64, expected.length());
        c.rejects("A different readable PNG cannot borrow the original capture marker", IllegalArgumentException.class,
                () -> AndroidStockfishSanityEvidence.validatePng(png(0xff262522), expected));
        byte[] broken = original.clone(); broken[0] = 0;
        c.rejects("Updating the metadata hash cannot turn non-PNG bytes into a capture", IllegalArgumentException.class,
                () -> AndroidStockfishSanityEvidence.validatePng(broken, AndroidStockfishSanityEvidence.sha(broken)));
        c.rejects("An empty capture never qualifies", IllegalArgumentException.class,
                () -> AndroidStockfishSanityEvidence.validatePng(new byte[0], AndroidStockfishSanityEvidence.sha(new byte[0])));
    }

    @Test void aMissingOrFailedOriginalExecutionCannotBorrowAnyReport(Checks c) throws Exception {
        c.rejects("No execution receipt is no native qualification", IllegalArgumentException.class,
                () -> AndroidStockfishSanityEvidence.verify(folder));
        Files.writeString(folder.resolve("native-exit.json"), "{\"schema\":1,\"exitCode\":1}");
        c.rejects("A native failure cannot be repaired by synthetic PASS assertions", IllegalArgumentException.class,
                () -> AndroidStockfishSanityEvidence.verify(folder));
        Files.writeString(folder.resolve("native-exit.json"), "{\"schema\":1,\"exitCode\":0,\"runId\":\"foreign\"}");
        c.rejects("A native 0 exit still needs its real run identity", IllegalArgumentException.class,
                () -> AndroidStockfishSanityEvidence.verify(folder));
    }

    @Test void rawUciMustRetainTheSameHistoricalPositionBudgetRootAndExactOutput(Checks c) {
        var match = ChessMatch.restore("2|analysis|e2e4"); var context = ExternalEngineAnalysis.context(match, 1);
        String info = "info depth 12 seldepth 14 multipv 1 score cp 35 wdl 500 300 200 nodes 200001 time 51 pv e7e5 g1f3";
        String raw = "> position startpos moves e2e4\n> go nodes 200000 searchmoves e7e5\n< " + info + "\n< bestmove e7e5\n";
        AndroidStockfishSanityEvidence.verifyCommands(raw, context, "e7e5", "e7e5");
        var line = StockfishTransport.info(info);
        c.same("The original exact score, WDL, work and full PV reconcile", true, AndroidStockfishSanityEvidence.rawContains(raw, line));
        for (String bad : List.of(raw.replace("moves e2e4", "moves d2d4"), raw.replace("nodes 200000", "nodes 6000"),
                raw.replace("searchmoves e7e5", "searchmoves c7c5"), raw.replace("< bestmove e7e5", "< bestmove c7c5"),
                raw + "> go nodes 200000 searchmoves e7e5\n"))
            c.rejects("Foreign history, reduced budget, forced root or missing drain is rejected", IllegalArgumentException.class,
                    () -> AndroidStockfishSanityEvidence.verifyCommands(bad, context, "e7e5", "e7e5"));
        for (String bad : List.of(raw.replace("score cp 35", "score cp 350"), raw.replace("500 300 200", "600 300 100"),
                raw.replace("pv e7e5 g1f3", "pv e7e5 b1c3"), raw.replace("score cp 35", "score cp 35 lowerbound")))
            c.same("A changed score, WDL, PV or bound cannot impersonate the retained exact observation", false,
                    AndroidStockfishSanityEvidence.rawContains(bad, line));
    }

    static String fixture() {
        var text = new StringBuilder("INSTRUMENTATION_RESULT: phase=stockfish-only\nINSTRUMENTATION_RESULT: pid=123\nINSTRUMENTATION_RESULT: runId=" + RUN + "\nINSTRUMENTATION_RESULT: result=PASS\nINSTRUMENTATION_CODE: -1\n");
        int index = 0;
        for (String name : AndroidStockfishSanityEvidence.NAMES) {
            index++;
            text.append(String.format(java.util.Locale.ROOT, "INSTRUMENTATION_RESULT: check_%02d=%s\texpected-value-%d\tactual-value-%d\ttrue\n", index, name, index, index));
        }
        return text.toString();
    }

    private record BadReceipt(String name, String content) { }

    private static byte[] png(int color) throws Exception {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, color);
        var out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out); return out.toByteArray();
    }
}
