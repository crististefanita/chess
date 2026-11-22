package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.Level;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class PortableSha256Test {
    @Test void matchesStandardSha256Vectors(Checks check) {
        check.same("The empty input has the standard SHA-256 digest",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(""));
        check.same("The abc input has the standard SHA-256 digest",
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex("abc"));
        check.same("A message spanning two blocks keeps the standard padding",
                "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
                hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"));
        byte[] millionA = new byte[1_000_000];
        java.util.Arrays.fill(millionA, (byte) 'a');
        check.same("A long message keeps the standard multi-block digest",
                "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
                PortableSha256.hex(millionA));
    }

    @Test void matchesTheJdkAcrossPaddingAndBlockBoundaries(Checks check) throws Exception {
        var random = new Random(0x534841323536L);
        for (int length : new int[]{0, 1, 2, 55, 56, 57, 63, 64, 65, 119, 120, 127, 128, 129, 1024, 4097}) {
            byte[] input = new byte[length];
            random.nextBytes(input);
            check.same("Portable SHA-256 matches JCA for " + length + " bytes",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)),
                    PortableSha256.hex(input));
        }
    }

    @Test void proofAndReviewIdentifiersKeepTheirExistingSha256Contracts(Checks check) throws Exception {
        var match = ChessMatch.start(new MatchSetup(Side.WHITE, Level.BASIC, false));
        var history = new ArrayList<ChessPosition>();
        history.add(match.positionAt(0));
        for (String move : List.of("e2e4", "d7d5", "e4d5")) {
            match = match.play(ChessPosition.Move.fromUci(move));
            history.add(match.positionAt(match.ply()));
        }
        var serializedHistory = new StringBuilder();
        for (var position : history) serializedHistory.append(position.searchKey()).append('\n');
        check.same("Hard proof history IDs retain the previous UTF-8 SHA-256 value",
                jcaHex(serializedHistory.toString().getBytes(StandardCharsets.UTF_8)), PawnPushProof.historyHash(history));
        check.same("Game Review checkpoints retain their previous Base64 SHA-256 value",
                Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                        .digest(match.checkpoint().getBytes(StandardCharsets.UTF_8))),
                GameReviewCodec.checkpointDigest(match));
    }

    private static String hex(String text) {
        return PortableSha256.hex(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String jcaHex(byte[] input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    }
}
