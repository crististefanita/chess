package ro.atf.chess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** Measured, versioned opening references; never an input to the playing AI. */
public final class OpeningReference {
    private static final class Current {
        static final OpeningReference VALUE = read(ChessYaml.resource("opening-reference.yaml"));
    }

    private record Entry(int whiteCp, int delta, List<TacticalAnalysis.Variation> lines) { }
    private final String engine;
    private final int depth;
    private final Map<String, Entry> entries;

    private OpeningReference(String engine, int depth, Map<String, Entry> entries) {
        this.engine = engine;
        this.depth = depth;
        this.entries = Map.copyOf(entries);
    }

    public static MatchHint analyze(OpeningCatalog.Opening opening, int ply) {
        return Current.VALUE.analyze(opening, ply, () -> MatchHint.analyze(opening.positionAt(ply)));
    }

    MatchHint analyze(OpeningCatalog.Opening opening, int ply, Supplier<MatchHint> live) {
        var entry = entries.get(prefix(opening, ply));
        return entry == null ? live.get() : MatchHint.reference(opening.positionAt(ply), entry.lines(),
                new MatchHint.Reference(engine, depth, entry.whiteCp(), entry.delta()));
    }

    static String prefix(OpeningCatalog.Opening opening, int ply) {
        if (ply < 0 || ply > opening.steps().size()) throw new IllegalArgumentException("Invalid opening ply");
        return opening.steps().stream().limit(ply).map(step -> step.move().uci()).collect(Collectors.joining(" "));
    }

    static OpeningReference read(Map<?, ?> root) {
        keys(root, "schema", "source", "positions");
        if (ChessYaml.number(root, "schema") != 1) throw new IllegalArgumentException("Unknown opening reference schema");
        var source = ChessYaml.map(root, "source");
        keys(source, "engine", "depth", "threads", "hash-mb", "multi-pv", "max-gap-cp", "perspective", "engine-sha256");
        String engine = text(source, "engine");
        int depth = ChessYaml.number(source, "depth"), gap = ChessYaml.number(source, "max-gap-cp");
        if (engine.isBlank() || depth < 1 || depth > 64 || gap < 0 || gap > 200
                || !"WHITE".equals(text(source, "perspective"))
                || !text(source, "engine-sha256").matches("[a-f0-9]{64}")
                || ChessYaml.number(source, "threads") != 1 || ChessYaml.number(source, "hash-mb") < 1
                || ChessYaml.number(source, "multi-pv") != 3) throw new IllegalArgumentException("Invalid reference producer");
        var allowed = new HashSet<String>();
        for (var opening : OpeningCatalog.load()) for (int ply = 0; ply <= opening.steps().size(); ply++) allowed.add(prefix(opening, ply));
        var entries = new HashMap<String, Entry>();
        for (var value : ChessYaml.list(root, "positions")) {
            if (!(value instanceof Map<?, ?> row)) throw new IllegalArgumentException("Invalid reference row");
            keys(row, "moves", "white-cp", "delta-white-cp", "lines");
            String moves = text(row, "moves");
            if (!allowed.contains(moves) || entries.containsKey(moves)) throw new IllegalArgumentException("Foreign or duplicate opening prefix: " + moves);
            var board = replay(ChessPosition.initial(), moves);
            int sign = board.turn() == Side.WHITE ? 1 : -1;
            int score = ChessYaml.number(row, "white-cp");
            if (score < -10000 || score > 10000) throw new IllegalArgumentException("Invalid reference score");
            var lines = new ArrayList<TacticalAnalysis.Variation>();
            var roots = new HashSet<Move>();
            int prior = Integer.MAX_VALUE;
            for (var item : ChessYaml.list(row, "lines")) {
                if (!(item instanceof Map<?, ?> line)) throw new IllegalArgumentException("Invalid reference line");
                keys(line, "white-cp", "moves");
                String path = text(line, "moves");
                var pv = moves(path);
                int whiteCp = ChessYaml.number(line, "white-cp");
                if (whiteCp < -10000 || whiteCp > 10000) throw new IllegalArgumentException("Invalid continuation score");
                int cp = whiteCp * sign;
                replay(board, path);
                if (pv.isEmpty() || pv.size() > 64 || !roots.add(pv.get(0)) || cp > prior || score * sign - cp > gap) throw new IllegalArgumentException("Invalid ordered reference continuation");
                prior = cp;
                lines.add(new TacticalAnalysis.Variation(pv, cp, 0, TacticalAnalysis.Ending.HORIZON));
            }
            if (lines.isEmpty() || lines.size() > 3 || lines.get(0).score() != score * sign)
                throw new IllegalArgumentException("Reference score and leader differ");
            entries.put(moves, new Entry(score, ChessYaml.number(row, "delta-white-cp"), List.copyOf(lines)));
        }
        for (var row : entries.entrySet()) {
            String path = row.getKey();
            var previous = path.isEmpty() ? null : entries.get(path.contains(" ") ? path.substring(0, path.lastIndexOf(' ')) : "");
            if (!path.isEmpty() && previous == null || row.getValue().delta() != (previous == null ? 0 : row.getValue().whiteCp() - previous.whiteCp()))
                throw new IllegalArgumentException("Inconsistent white-perspective delta at " + path);
        }
        return new OpeningReference(engine, depth, entries);
    }

    private static List<Move> moves(String path) {
        return path.isEmpty() ? List.of() : java.util.Arrays.stream(path.split(" ")).map(Move::fromUci).collect(Collectors.toList());
    }

    private static ChessPosition replay(ChessPosition position, String path) {
        for (var move : moves(path)) position = position.play(move);
        return position;
    }

    private static String text(Map<?, ?> row, String key) {
        if (row.get(key) instanceof String value) return value;
        throw new IllegalArgumentException("Expected text for " + key);
    }

    private static void keys(Map<?, ?> value, String... names) {
        if (!value.keySet().equals(Set.of(names))) throw new IllegalArgumentException("Unknown or missing reference fields");
    }
}
