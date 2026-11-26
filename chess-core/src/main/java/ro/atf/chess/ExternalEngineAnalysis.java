package ro.atf.chess;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** Validated external engine observations. This boundary never starts a chess search. */
public final class ExternalEngineAnalysis {
    public static final String INITIAL_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    public static final String PROOF_STATUS = "ENGINE_SEARCH_NOT_EXHAUSTIVE_PROOF";
    private ExternalEngineAnalysis() { }

    public record Context(String checkpoint, int ply, String contextKey, String initialFen, List<String> moves,
                          String rootFen, List<String> legalMoves, String result) {
        public Context { moves = HardCollections.copyList(moves); legalMoves = HardCollections.copyList(legalMoves); }
        public String json() {
            return "{\"checkpoint\":" + quote(checkpoint) + ",\"ply\":" + ply + ",\"contextKey\":" + quote(contextKey)
                    + ",\"initialFen\":" + quote(initialFen) + ",\"moves\":" + strings(moves) + ",\"rootFen\":" + quote(rootFen)
                    + ",\"legalMoves\":" + strings(legalMoves) + ",\"result\":" + quote(result) + "}";
        }
    }

    public record Provider(String providerID, String version, String buildSha256, String sourceCommit, String optionsIdentity) {
        public Provider {
            if (providerID == null || !providerID.startsWith("stockfish-") || providerID.length() > 128
                    || version == null || version.isBlank() || version.length() > 128
                    || buildSha256 == null || !buildSha256.matches("[0-9a-f]{64}")
                    || sourceCommit == null || !sourceCommit.matches("[0-9a-f]{40}")
                    || optionsIdentity == null || optionsIdentity.isBlank() || optionsIdentity.length() > 2048) {
                throw new IllegalArgumentException("Missing external engine provenance");
            }
            var options = object(parse(optionsIdentity));
            if (!Long.valueOf(1).equals(options.get("threads")) || !Boolean.FALSE.equals(options.get("limitStrength"))
                    || !(options.get("hashMb") instanceof Long hash) || hash < 1 || hash > 1024
                    || !(options.get("nodes") instanceof Long nodes) || nodes < 1) {
                throw new IllegalArgumentException("External coaching must retain full strength and its resource identity");
            }
        }
        public String json() {
            return "{\"providerID\":" + quote(providerID) + ",\"version\":" + quote(version)
                    + ",\"buildSha256\":" + quote(buildSha256) + ",\"sourceCommit\":" + quote(sourceCommit)
                    + ",\"optionsIdentity\":" + quote(optionsIdentity) + ",\"proofStatus\":" + quote(PROOF_STATUS) + "}";
        }
    }

    public record Line(int rank, int depth, Integer cp, Integer mate, List<Integer> wdl, List<Move> pv,
                       Long nodes, Integer seldepth, Long timeMs) {
        public Line(int rank, int depth, Integer cp, Integer mate, List<Integer> wdl, List<Move> pv) {
            this(rank, depth, cp, mate, wdl, pv, null, null, null);
        }
        public Line {
            wdl = HardCollections.copyList(wdl); pv = HardCollections.copyList(pv);
            if (rank < 1 || rank > 512 || depth < 1 || depth > 255 || (cp == null) == (mate == null)
                    || cp != null && Math.abs((long) cp) > 50000 || mate != null && (mate == 0 || Math.abs((long) mate) > 1000)
                    || pv.isEmpty() || pv.size() > 256 || wdl.size() != 3
                    || nodes != null && nodes < 0 || seldepth != null && seldepth < 0 || timeMs != null && timeMs < 0
                    || wdl.stream().anyMatch(n -> n < 0 || n > 1000) || wdl.stream().mapToInt(Integer::intValue).sum() != 1000) {
                throw new IllegalArgumentException("Invalid exact external engine line");
            }
        }
        public Move root() { return pv.get(0); }
        long orderValue() { return mate == null ? cp : mate > 0 ? 1_000_000L - mate : -1_000_000L - mate; }
        public int expectedPoints() { return (int) Math.round(wdl.get(0) + wdl.get(1) / 2.0); }
        public MatchEvaluation evaluation(Side turn) {
            int sign = turn == Side.WHITE ? 1 : -1;
            return new MatchEvaluation(mate == null ? TacticalAnalysis.Ending.HORIZON : TacticalAnalysis.Ending.CHECKMATE,
                    cp == null ? 0 : sign * cp, mate == null ? 0 : sign * mate);
        }
        /** HORIZON avoids presenting an engine mate estimate as a replayed mate proof. */
        public TacticalAnalysis.Variation variation() {
            return new TacticalAnalysis.Variation(pv, cp == null ? Integer.signum(mate) * 50000 : cp, 0, TacticalAnalysis.Ending.HORIZON);
        }
        String json() {
            return "{\"rank\":" + rank + ",\"depth\":" + depth + ",\"score\":{\"type\":" + quote(cp == null ? "mate" : "cp")
                    + ",\"value\":" + (cp == null ? mate : cp) + "},\"wdl\":[" + wdl.get(0) + "," + wdl.get(1) + "," + wdl.get(2)
                    + "],\"pv\":" + strings(pv.stream().map(Move::uci).collect(java.util.stream.Collectors.toList())) + ",\"bound\":\"EXACT\""
                    + ",\"nodes\":" + nodes + ",\"seldepth\":" + seldepth + ",\"timeMs\":" + timeMs + "}";
        }
    }

    public record Batch(Context context, Provider provider, int depth, List<Line> lines) {
        public Batch { lines = HardCollections.copyList(lines); }
        public Line best() { return lines.get(0); }
        public String json() {
            var out = new StringBuilder(provider.json()); out.setLength(out.length() - 1);
            out.append(",\"contextKey\":").append(quote(context.contextKey())).append(",\"rootFen\":").append(quote(context.rootFen()))
                    .append(",\"depth\":").append(depth).append(",\"bestmove\":").append(quote(best().root().uci())).append(",\"lines\":[");
            for (var line : lines) { if (out.charAt(out.length() - 1) != '[') out.append(','); out.append(line.json()); }
            return out.append("]}").toString();
        }
    }

    public record ReviewedTurn(int ply, Batch free, Batch played) { }
    public record Review(String contextKey, List<ReviewedTurn> turns, boolean rated) {
        public Review(String contextKey, List<ReviewedTurn> turns) { this(contextKey, turns, false); }
        public Review { turns = HardCollections.copyList(turns); }
        public Provider provider() { return turns.get(0).free().provider(); }
        public String json() {
            var out = new StringBuilder("{\"contextKey\":").append(quote(contextKey)).append(",\"rated\":").append(rated).append(",\"turns\":[");
            for (var turn : turns) {
                if (out.charAt(out.length() - 1) != '[') out.append(',');
                out.append("{\"ply\":").append(turn.ply()).append(",\"free\":").append(turn.free().json())
                        .append(",\"played\":").append(turn.played().json()).append('}');
            }
            return out.append("]}").toString();
        }
    }

    public static Context context(ChessMatch match, int ply) {
        if (ply < 0 || ply > match.ply()) throw new IllegalArgumentException("External analysis ply is outside the match");
        if (!match.positionAt(0).sameForRepetition(ChessPosition.initial())) throw new IllegalArgumentException("Unsupported external initial position");
        var moves = new ArrayList<String>();
        for (int i = 1; i <= ply; i++) moves.add(match.moveAt(i).uci());
        var board = match.positionAt(ply);
        var legal = board.legalMoves().stream().map(Move::uci).collect(java.util.stream.Collectors.toList());
        var line = match.continueFrom(ply);
        String ep = "-";
        if (ply > 0) {
            var last = match.moveAt(ply);
            if (match.positionAt(ply - 1).pieceAt(last.from()).kind() == ChessPosition.Kind.PAWN && Math.abs(last.to() - last.from()) == 16)
                ep = BoardSquare.name((last.from() + last.to()) / 2);
        }
        String rootFen = placement(board) + (board.turn() == Side.WHITE ? " w " : " b ") + rights(board.searchKey().castleRights())
                + " " + ep + " " + line.reversiblePlies() + " " + (1 + ply / 2);
        String checkpoint = match.checkpoint();
        String key = digest(checkpoint + "\n" + ply + "\n" + INITIAL_FEN + "\n" + String.join(" ", moves) + "\n" + rootFen);
        return new Context(checkpoint, ply, key, INITIAL_FEN, moves, rootFen, legal, line.result().name());
    }

    public static Batch batch(ChessMatch match, int ply, String json) { return batch(match, context(match, ply), object(parse(json))); }

    private static Batch batch(ChessMatch match, Context context, Map<String, Object> data) {
        if (!context.contextKey().equals(text(data, "contextKey")) || !context.rootFen().equals(text(data, "rootFen")))
            throw new IllegalArgumentException("External analysis belongs to another full context");
        var provider = new Provider(text(data, "providerID"), text(data, "version"), text(data, "buildSha256"),
                text(data, "sourceCommit"), text(data, "optionsIdentity"));
        int depth = integer(data.get("depth"));
        var values = array(data.get("lines"));
        if (values.isEmpty() || values.size() > context.legalMoves().size()) throw new IllegalArgumentException("Missing external engine lines");
        var lines = new ArrayList<Line>();
        for (int i = 0; i < values.size(); i++) {
            var row = object(values.get(i)); var score = object(row.get("score"));
            String type = text(score, "type");
            if (!type.equals("cp") && !type.equals("mate") || !"EXACT".equals(text(row, "bound")))
                throw new IllegalArgumentException("External engine score is not exact");
            var pv = array(row.get("pv")).stream().map(v -> Move.fromUci(string(v))).collect(java.util.stream.Collectors.toList());
            var wdl = array(row.get("wdl")).stream().map(ExternalEngineAnalysis::integer).collect(java.util.stream.Collectors.toList());
            var line = new Line(integer(row.get("rank")), integer(row.get("depth")), type.equals("cp") ? integer(score.get("value")) : null,
                    type.equals("mate") ? integer(score.get("value")) : null, wdl, pv,
                    optionalLong(row.get("nodes")), row.get("seldepth") == null ? null : integer(row.get("seldepth")), optionalLong(row.get("timeMs")));
            if (line.rank() != i + 1 || line.depth() != depth || lines.stream().anyMatch(old -> old.root().equals(line.root())))
                throw new IllegalArgumentException("External engine lines are not a common completed depth");
            if (!lines.isEmpty() && lines.get(lines.size() - 1).orderValue() < line.orderValue())
                throw new IllegalArgumentException("External principal variations are not ranked by score");
            var board = match.positionAt(context.ply());
            for (var move : line.pv()) board = board.play(move);
            lines.add(line);
        }
        if (!lines.get(0).root().uci().equals(text(data, "bestmove"))) throw new IllegalArgumentException("External bestmove differs from exact PV");
        return new Batch(context, provider, depth, lines);
    }

    public static Review review(ChessMatch match, String json) {
        var data = object(parse(json));
        String key = context(match, match.ply()).contextKey();
        if (!key.equals(text(data, "contextKey")) || match.ply() == 0) throw new IllegalArgumentException("External review belongs to another checkpoint");
        var rows = array(data.get("turns"));
        if (rows.size() != match.ply()) throw new IllegalArgumentException("Incomplete external review");
        var turns = new ArrayList<ReviewedTurn>(); Provider provider = null;
        for (int index = 0; index < rows.size(); index++) {
            int ply = index + 1; var row = object(rows.get(index));
            if (integer(row.get("ply")) != ply) throw new IllegalArgumentException("External review ply differs");
            var context = context(match, index);
            var free = batch(match, context, object(row.get("free")));
            var played = batch(match, context, object(row.get("played")));
            if (!played.best().root().equals(match.moveAt(ply)) || free.depth() != played.depth() || !free.provider().equals(played.provider())
                    || provider != null && !provider.equals(free.provider())) throw new IllegalArgumentException("External review needs matched free and played observations");
            provider = free.provider(); turns.add(new ReviewedTurn(ply, free, played));
        }
        Object rated = data.get("rated");
        if (rated != null && !(rated instanceof Boolean)) throw new IllegalArgumentException("Invalid review rating eligibility");
        return new Review(key, turns, Boolean.TRUE.equals(rated));
    }

    private static String rights(int value) {
        String text = ((value & 2) != 0 ? "K" : "") + ((value & 1) != 0 ? "Q" : "")
                + ((value & 8) != 0 ? "k" : "") + ((value & 4) != 0 ? "q" : "");
        return text.isEmpty() ? "-" : text;
    }
    private static String placement(ChessPosition board) {
        var out = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            if (rank != 7) out.append('/'); int empty = 0;
            for (int file = 0; file < 8; file++) {
                var piece = board.pieceAt(BoardSquare.of(file, rank));
                if (piece == null) { empty++; continue; }
                if (empty > 0) { out.append(empty); empty = 0; }
                char c = "pnbrqk".charAt(piece.kind().ordinal()); out.append(piece.side() == Side.WHITE ? Character.toUpperCase(c) : c);
            }
            if (empty > 0) out.append(empty);
        }
        return out.toString();
    }
    public static String quote(String text) {
        if (text == null) return "null";
        var out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) { case '\\' -> out.append("\\\\"); case '"' -> out.append("\\\""); case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t"); default -> {
                    if (c < 32) { String hex = Integer.toHexString(c); out.append("\\u").append("0000".substring(hex.length())).append(hex); }
                    else out.append(c);
                } }
        }
        return out.append('"').toString();
    }
    private static String strings(List<String> values) { return "[" + String.join(",", values.stream().map(ExternalEngineAnalysis::quote).collect(java.util.stream.Collectors.toList())) + "]"; }
    private static String digest(String text) {
        var out = new StringBuilder();
        for (byte b : PortableSha256.digest(text.getBytes(StandardCharsets.UTF_8))) { int n = b & 255; if (n < 16) out.append('0'); out.append(Integer.toHexString(n)); }
        return out.toString();
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map)) throw new IllegalArgumentException("Expected external analysis object"); return (Map<String, Object>) value;
    }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value) {
        if (!(value instanceof List)) throw new IllegalArgumentException("Expected external analysis array"); return (List<Object>) value;
    }
    private static String text(Map<String, Object> map, String key) { return string(map.get(key)); }
    private static String string(Object value) { if (!(value instanceof String)) throw new IllegalArgumentException("Expected external analysis text"); return (String) value; }
    private static int integer(Object value) {
        if (!(value instanceof Long n) || n < Integer.MIN_VALUE || n > Integer.MAX_VALUE) throw new IllegalArgumentException("Expected external analysis integer"); return ((Long) value).intValue();
    }
    private static Long optionalLong(Object value) {
        if (value == null) return null;
        if (!(value instanceof Long n) || n < 0) throw new IllegalArgumentException("Invalid external engine work");
        return n;
    }
    private static Object parse(String text) {
        if (text == null || text.length() > 4_000_000) throw new IllegalArgumentException("Invalid external analysis size");
        var parser = new Json(text); var value = parser.value(0); parser.space();
        if (parser.index != text.length()) throw new IllegalArgumentException("Unexpected external analysis data"); return value;
    }
    private static final class Json {
        private final String text; private int index;
        Json(String text) { this.text = text; }
        void space() { while (index < text.length() && " \r\n\t".indexOf(text.charAt(index)) >= 0) index++; }
        Object value(int depth) {
            space(); if (depth > 32 || index >= text.length()) throw bad(); char c = text.charAt(index);
            if (c == '"') return string();
            if (c == '{') {
                index++; var map = new LinkedHashMap<String, Object>(); space(); if (take('}')) return map;
                do { space(); if (index >= text.length() || text.charAt(index) != '"') throw bad(); String key = string(); space(); if (!take(':')) throw bad();
                    if (map.containsKey(key)) throw bad(); map.put(key, value(depth + 1)); space(); if (take('}')) return map;
                } while (take(',')); throw bad();
            }
            if (c == '[') { index++; var list = new ArrayList<Object>(); space(); if (take(']')) return list;
                do { list.add(value(depth + 1)); space(); if (take(']')) return list; } while (take(',')); throw bad(); }
            for (String literal : HardCollections.list("null", "true", "false")) if (text.startsWith(literal, index)) { index += literal.length(); return literal.equals("null") ? null : literal.equals("true"); }
            int start = index; if (c == '-') index++; while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') index++;
            String number = text.substring(start, index); if (!number.matches("-?(0|[1-9][0-9]*)")) throw bad();
            try { return Long.parseLong(number); } catch (NumberFormatException invalid) { throw bad(); }
        }
        String string() {
            index++; var out = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++); if (c == '"') return out.toString(); if (c < 32) throw bad();
                if (c != '\\') { out.append(c); continue; } if (index >= text.length()) throw bad(); c = text.charAt(index++);
                switch (c) { case '"', '\\', '/' -> out.append(c); case 'b' -> out.append('\b'); case 'f' -> out.append('\f'); case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r'); case 't' -> out.append('\t'); case 'u' -> { if (index + 4 > text.length()) throw bad();
                        try { out.append((char) Integer.parseInt(text.substring(index, index + 4), 16)); } catch (NumberFormatException invalid) { throw bad(); } index += 4; }
                    default -> throw bad(); }
            } throw bad();
        }
        boolean take(char c) { if (index < text.length() && text.charAt(index) == c) { index++; return true; } return false; }
        IllegalArgumentException bad() { return new IllegalArgumentException("Malformed external analysis JSON at " + index); }
    }
}
