package ro.atf.chess;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import ro.atf.chess.ChessPosition.Move;

/** Persistent, contiguous native review evidence. Classification happens only during aggregation. */
public final class ReviewJournal {
    private static final int VERSION = 1;
    private static final int MAX_PLIES = 4096;
    private static final class PolicyIdentity { static final String VALUE = policyIdentity(); }
    public record Sample(int best, int actual, int alternative, int depth, int sacrifice, int ordinaryNodes, boolean bestMove,
                         boolean onlyMate, List<Move> bestLine, List<Move> playedLine, CoachingProof.Work proof) {
        public Sample { bestLine = List.copyOf(bestLine); playedLine = List.copyOf(playedLine); }
    }
    private final List<Sample> samples = new ArrayList<>();
    private ChessMatch source;
    private String restoration = "EMPTY";

    public ReviewJournal(ChessMatch match) { source = match; }
    public int completed() { return samples.size(); }
    public String restoration() { return restoration; }

    public void align(ChessMatch match) {
        if (!source.continueFrom(0).checkpoint().equals(match.continueFrom(0).checkpoint())) samples.clear();
        int keep = Math.min(samples.size(), match.ply());
        for (int ply = 1; ply <= keep; ply++) if (!source.moveAt(ply).equals(match.moveAt(ply))) { keep = ply - 1; break; }
        while (samples.size() > keep) samples.remove(samples.size() - 1);
        source = match;
    }

    Sample obtain(int ply, Supplier<Sample> calculate) {
        if (ply <= samples.size()) return samples.get(ply - 1);
        if (ply != samples.size() + 1 || ply > source.ply() || ply > MAX_PLIES) throw new IllegalArgumentException("Noncontiguous review work");
        var value = calculate.get();
        validate(source, ply, value);
        samples.add(value);
        return value;
    }

    Sample require(int ply) {
        if (ply < 1 || ply > samples.size()) throw new IllegalStateException("Review evidence missing at ply " + ply);
        return samples.get(ply - 1);
    }

    public String encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(VERSION); out.writeUTF(policy());
                byte[] checkpoint = source.continueFrom(samples.size()).checkpoint().getBytes(StandardCharsets.UTF_8);
                out.writeInt(checkpoint.length); out.write(checkpoint); out.writeInt(samples.size());
                for (var sample : samples) {
                    for (int n : new int[]{sample.best(), sample.actual(), sample.alternative(), sample.depth(), sample.sacrifice(), sample.ordinaryNodes()}) out.writeInt(n);
                    out.writeBoolean(sample.bestMove()); out.writeBoolean(sample.onlyMate());
                    GameReviewCodec.writeLine(out, sample.bestLine()); GameReviewCodec.writeLine(out, sample.playedLine());
                    GameReviewCodec.writeProof(out, sample.proof());
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException invalid) { throw new IllegalArgumentException("Cannot save review work", invalid); }
    }

    public static ReviewJournal restore(ChessMatch match, String encoded) {
        var fresh = new ReviewJournal(match);
        if (encoded == null || encoded.isEmpty()) return fresh;
        try (var in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            if (in.readInt() != VERSION || !in.readUTF().equals(policy())) throw new IllegalArgumentException("Stale review policy");
            int length = in.readInt();
            if (length < 1 || length > 500_000) throw new IllegalArgumentException("Invalid checkpoint length");
            var checkpoint = new byte[length];
            in.readFully(checkpoint);
            var saved = ChessMatch.restore(new String(checkpoint, StandardCharsets.UTF_8));
            int count = in.readInt();
            if (count < 0 || count > MAX_PLIES || count != saved.ply()) throw new IllegalArgumentException("Invalid review count");
            var restored = new ReviewJournal(saved);
            for (int ply = 1; ply <= count; ply++) {
                var sample = new Sample(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                        in.readBoolean(), in.readBoolean(), GameReviewCodec.readLine(in), GameReviewCodec.readLine(in), GameReviewCodec.readProof(in, 3));
                validate(saved, ply, sample); restored.samples.add(sample);
            }
            if (in.read() != -1) throw new IllegalArgumentException("Unexpected review work bytes");
            restored.align(match); restored.restoration = restored.completed() == count ? "REUSED" : "TRIMMED";
            return restored;
        } catch (IOException | IllegalArgumentException | IllegalStateException invalid) {
            fresh.restoration = "REJECTED";
            return fresh;
        }
    }

    private static void validate(ChessMatch match, int ply, Sample value) {
        if (value.best() < 0 || value.best() > 1000 || value.actual() < 0 || value.actual() > 1000
                || value.alternative() < 0 || value.alternative() > 1000 || value.depth() < 1 || value.depth() > 64
                || value.sacrifice() < 0 || value.bestLine().isEmpty() || value.playedLine().isEmpty()
                || value.ordinaryNodes() < 0 || !value.playedLine().get(0).equals(match.moveAt(ply))
                || value.proof() != null && !value.proof().complete())
            throw new IllegalArgumentException("Incomplete review evidence at " + ply);
        var before = match.positionAt(ply - 1);
        if (value.proof() != null) value.proof().validate(before, value.depth());
        for (var line : List.of(value.bestLine(), value.playedLine())) {
            if (line.size() > GameReviewCodec.MAX_LINE_LENGTH) throw new IllegalArgumentException("Review line too long");
            var board = before;
            for (var move : line) board = board.play(move);
        }
    }

    static String policy() {
        return PolicyIdentity.VALUE;
    }

    private static String policyIdentity() {
        StringBuilder text = new StringBuilder("review-work-").append(VERSION);
        for (String name : List.of("chess-rules.yaml", "game-review.yaml", "match-ai.yaml", "tactical-analysis.yaml",
                "coaching-proof.yaml", "ai-repertoire.yaml", "pawn-blockade.yaml", "hard-pawn-lever.yaml",
                "hard-pawn-promotion.yaml", "hard-promotion-exchange.yaml", "queen-reply-verification.yaml"))
            text.append('|').append(name).append(':').append(canonical(ChessYaml.resource(name)));
        return Base64.getEncoder().encodeToString(PortableSha256.digest(text.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static String canonical(Object value) {
        if (value instanceof Map<?, ?> map) return map.keySet().stream().map(Object::toString).sorted()
                .map(key -> canonical(key) + canonical(map.get(key))).collect(java.util.stream.Collectors.joining("", "{", "}"));
        if (value instanceof List<?> list) return list.stream().map(ReviewJournal::canonical).collect(java.util.stream.Collectors.joining("", "[", "]"));
        String text = String.valueOf(value); return value.getClass().getSimpleName() + text.length() + ":" + text;
    }
}
