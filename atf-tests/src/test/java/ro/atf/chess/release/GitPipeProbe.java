package ro.atf.chess.release;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/** Child-process probe; deliberately runs with a single virtual-thread carrier. */
public final class GitPipeProbe {
    private GitPipeProbe() { }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        byte[] payload = new byte[262_144];
        for (int index = 0; index < payload.length; index++) payload[index] = (byte) index;
        var digest = MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + payload.length + "\0").getBytes(StandardCharsets.UTF_8));
        String expected = HexFormat.of().formatHex(digest.digest(payload));
        String observed = new String(GitTestChanges.command(root, payload, "hash-object", "--stdin"), StandardCharsets.UTF_8).strip();
        if (!expected.equals(observed)) throw new IllegalStateException("Incomplete Git stdin or stdout");
        byte[] readme = GitTestChanges.command(root, new byte[0], "show", "HEAD:README.md");
        byte[] queries = "HEAD:README.md\n".repeat(16).getBytes(StandardCharsets.UTF_8);
        byte[] response = GitTestChanges.command(root, queries, "cat-file", "--batch");
        int cursor = 0;
        for (int batch = 0; batch < 16; batch++) {
            int header = cursor;
            while (cursor < response.length && response[cursor] != '\n') cursor++;
            if (cursor == response.length) throw new IllegalStateException("Missing Git batch header");
            String[] fields = new String(response, header, cursor - header, StandardCharsets.UTF_8).split(" ");
            if (fields.length != 3 || !fields[1].equals("blob") || Integer.parseInt(fields[2]) != readme.length) {
                throw new IllegalStateException("Wrong Git batch boundary");
            }
            cursor++;
            if (cursor + readme.length >= response.length || !Arrays.equals(readme, Arrays.copyOfRange(response, cursor, cursor + readme.length))) {
                throw new IllegalStateException("Incomplete Git batch body");
            }
            cursor += readme.length;
            if (response[cursor++] != '\n') throw new IllegalStateException("Missing Git batch separator");
        }
        if (cursor != response.length) throw new IllegalStateException("Extra Git batch data");
        System.out.println("PASS hash=262144 batch=16");
    }
}
