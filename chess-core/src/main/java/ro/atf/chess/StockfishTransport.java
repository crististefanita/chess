package ro.atf.chess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import ro.atf.chess.ChessPosition.Move;

/** Strict UCI transport. Chess legality and full-context binding belong to ExternalEngineAnalysis. */
public final class StockfishTransport implements AutoCloseable {
    public interface Channel extends AutoCloseable {
        void send(String command) throws IOException;
        String receive(long timeoutMillis) throws IOException, InterruptedException;
        @Override void close();
    }

    public record Search(ExternalEngineAnalysis.Batch batch, Map<Integer, ExternalEngineAnalysis.Batch> completed,
                         String transcript, String bestmove) {
        public Search { completed = Collections.unmodifiableMap(new LinkedHashMap<>(completed)); }
    }

    private final Channel channel;
    private final ExternalEngineAnalysis.Provider provider;
    private final long nodes;
    private final int multiPv;
    private boolean ready;
    private volatile boolean closed;
    private final Map<String, String> networks = new LinkedHashMap<>();
    private StringBuilder transcript;
    private static final int MAX_TRANSCRIPT = 2 * 1024 * 1024;

    public StockfishTransport(Channel channel, ExternalEngineAnalysis.Provider provider, long nodes, int multiPv) {
        if (nodes < 1 || multiPv < 1 || multiPv > 5) throw new IllegalArgumentException("Invalid coaching search policy");
        String policy = "{\"threads\":1,\"hashMb\":32,\"nodes\":" + nodes + ",\"limitStrength\":false}";
        if (!policy.equals(provider.optionsIdentity())) throw new IllegalArgumentException("Provider options do not match UCI commands");
        this.channel = channel; this.provider = provider; this.nodes = nodes; this.multiPv = multiPv;
    }

    public static StockfishTransport process(Process process, ExternalEngineAnalysis.Provider provider, long nodes, int multiPv) {
        return new StockfishTransport(new ProcessChannel(process), provider, nodes, multiPv);
    }

    public ExternalEngineAnalysis.Provider provider() { return provider; }

    /** Android stores the pinned NNUE as data, outside the executable directory. */
    public synchronized StockfishTransport network(String path) {
        if (ready || closed) throw new IllegalStateException("Configure networks before UCI initialization");
        if (path == null || path.isBlank() || path.indexOf('\n') >= 0 || path.indexOf('\r') >= 0 || path.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid NNUE path");
        networks.put("EvalFile", path); return this;
    }

    /** Each command is drained through bestmove. Interruption invalidates the process instead of reusing stale output. */
    public synchronized Search search(ChessMatch match, int ply, Move forced) {
        if (closed) throw new IllegalStateException("Stockfish is closed");
        var context = ExternalEngineAnalysis.context(match, ply);
        if (context.legalMoves().isEmpty() || !"IN_PROGRESS".equals(context.result()))
            throw new IllegalArgumentException("No live engine search at a terminal position");
        if (forced != null && !context.legalMoves().contains(forced.uci())) throw new IllegalArgumentException("Illegal forced root");
        transcript = new StringBuilder();
        try {
            initialize();
            send("ucinewgame");
            int expected = forced == null ? Math.min(multiPv, context.legalMoves().size()) : 1;
            send("setoption name MultiPV value " + expected);
            send("isready"); await("readyok", 10_000, ignored -> { });
            send("position startpos" + (context.moves().isEmpty() ? "" : " moves " + String.join(" ", context.moves())));
            send("go nodes " + nodes + (forced == null ? "" : " searchmoves " + forced.uci()));
            var rounds = new TreeMap<Integer, List<ExternalEngineAnalysis.Line>>();
            var completed = new TreeMap<Integer, ExternalEngineAnalysis.Batch>();
            var exactTops = new ArrayList<ExternalEngineAnalysis.Line>();
            String finish = await("bestmove ", 120_000, raw -> {
                var observation = information(raw);
                if (observation == null) return;
                var line = observation.line();
                if (line.rank() > expected) throw new IllegalArgumentException("Unexpected Stockfish rank");
                // A bounded rank-one update starts another cohort too; never join its tails to an older exact top.
                if (observation.bounded()) { rounds.remove(line.depth()); return; }
                var rows = rounds.get(line.depth());
                if (line.rank() == 1 || rows == null) {
                    rows = new ArrayList<>();
                    rounds.put(line.depth(), rows);
                }
                if (line.rank() != rows.size() + 1) { rows.clear(); return; }
                rows.add(line);
                if (line.rank() == 1) exactTops.add(line);
                if (rows.size() == expected) {
                    var batch = validated(match, ply, context, line.depth(), rows);
                    completed.put(line.depth(), batch);
                }
            });
            String[] end = finish.trim().split("\\s+");
            if ((end.length != 2 && end.length != 4) || end.length == 4 && (!"ponder".equals(end[2]) || !uci(end[3])) || !uci(end[1]))
                throw new IllegalArgumentException("Malformed Stockfish bestmove");
            String best = end[1];
            ExternalEngineAnalysis.Line top = null;
            for (var row : exactTops) if (row.root().uci().equals(best) && (top == null || row.depth() >= top.depth())) top = row;
            if (top == null || forced != null && !best.equals(forced.uci())) throw new IllegalArgumentException("No exact PV for Stockfish bestmove");
            if (completed.isEmpty()) throw new IllegalArgumentException("No complete exact Stockfish iteration");
            var batch = completed.lastEntry().getValue();
            return new Search(batch, completed, transcript.toString(), best);
        } catch (InterruptedException interrupted) {
            close(); Thread.currentThread().interrupt(); throw new CancellationException("Stockfish request interrupted");
        } catch (IOException failed) {
            close(); throw new IllegalStateException("Stockfish transport failed", failed);
        } catch (RuntimeException failed) {
            close(); throw failed;
        } finally { transcript = null; }
    }

    private ExternalEngineAnalysis.Batch validated(ChessMatch match, int ply, ExternalEngineAnalysis.Context context, int depth,
                                                   List<ExternalEngineAnalysis.Line> rows) {
        return ExternalEngineAnalysis.batch(match, ply, new ExternalEngineAnalysis.Batch(context, provider, depth, rows).json());
    }

    private void initialize() throws IOException, InterruptedException {
        if (ready) return;
        send("uci");
        var identity = new StringBuilder(); var options = new StringBuilder();
        await("uciok", 120_000, row -> {
            if (row.startsWith("id name ")) identity.append(row.substring(8));
            if (row.startsWith("option name ")) options.append(row).append('\n');
        });
        if (!identity.toString().matches("Stockfish 19(?:\\b.*)?")) throw new IllegalArgumentException("Unexpected Stockfish identity");
        for (String required : HardCollections.list("Threads", "Hash", "MultiPV", "Skill Level", "UCI_LimitStrength", "UCI_ShowWDL", "Ponder"))
            if (!options.toString().contains("option name " + required + " type ")) throw new IllegalArgumentException("Missing UCI option " + required);
        for (String network : networks.keySet()) if (!options.toString().contains("option name " + network + " type "))
            throw new IllegalArgumentException("Missing UCI network option " + network);
        send("setoption name Threads value 1"); send("setoption name Hash value 32");
        send("setoption name Skill Level value 20"); send("setoption name UCI_LimitStrength value false");
        send("setoption name UCI_ShowWDL value true"); send("setoption name Ponder value false");
        for (var network : networks.entrySet()) send("setoption name " + network.getKey() + " value " + network.getValue());
        send("isready"); await("readyok", 120_000, ignored -> { }); ready = true;
    }

    private String await(String prefix, long timeout, Consumer<String> observation) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remaining <= 0) throw new IOException("Stockfish timeout waiting for " + prefix.trim());
            String row = channel.receive(Math.min(remaining, 250));
            if (row == null) continue;
            append("< " + row);
            if (row.equals(prefix) || prefix.endsWith(" ") && row.startsWith(prefix)) return row;
            observation.accept(row);
        }
    }

    private void send(String command) throws IOException { append("> " + command); channel.send(command); }
    private void append(String line) {
        if (line.length() > 65536 || transcript.length() + line.length() + 1 > MAX_TRANSCRIPT)
            throw new IllegalArgumentException("Stockfish transcript limit exceeded");
        transcript.append(line).append('\n');
    }

    static ExternalEngineAnalysis.Line info(String raw) {
        var value = information(raw); return value == null || value.bounded() ? null : value.line();
    }
    private record Information(ExternalEngineAnalysis.Line line, boolean bounded) { }
    private static Information information(String raw) {
        if (!raw.startsWith("info ") || raw.startsWith("info string ")) return null;
        String[] t = raw.trim().split("\\s+");
        Integer depth = null, cp = null, mate = null, seldepth = null; int rank = 1;
        Long nodes = null, time = null; var wdl = new ArrayList<Integer>(); var pv = new ArrayList<Move>();
        boolean bound = false, scored = false;
        try {
            for (int i = 1; i < t.length; i++) {
                switch (t[i]) {
                    case "depth" -> depth = Integer.valueOf(t[++i]);
                    case "seldepth" -> seldepth = Integer.valueOf(t[++i]);
                    case "multipv" -> rank = Integer.parseInt(t[++i]);
                    case "nodes" -> nodes = Long.valueOf(t[++i]);
                    case "time" -> time = Long.valueOf(t[++i]);
                    case "lowerbound", "upperbound" -> bound = true;
                    case "score" -> {
                        String type = t[++i]; int value = Integer.parseInt(t[++i]); scored = true;
                        if ("cp".equals(type)) cp = value; else if ("mate".equals(type)) mate = value;
                        else throw new IllegalArgumentException("Unknown UCI score");
                    }
                    case "wdl" -> { wdl.clear(); for (int j = 0; j < 3; j++) wdl.add(Integer.parseInt(t[++i])); }
                    case "pv" -> { while (++i < t.length) { if (!uci(t[i])) throw new IllegalArgumentException("Malformed PV move"); pv.add(Move.fromUci(t[i])); } }
                    default -> { }
                }
            }
        } catch (IndexOutOfBoundsException | NumberFormatException malformed) { throw new IllegalArgumentException("Malformed Stockfish info", malformed); }
        if (!scored || pv.isEmpty() || depth == null || depth == 0) return null;
        if (nodes == null || time == null || wdl.size() != 3) throw new IllegalArgumentException("Stockfish scored PV lacks work or WDL");
        return new Information(new ExternalEngineAnalysis.Line(rank, depth, cp, mate, wdl, pv, nodes, seldepth, time), bound);
    }

    private static boolean uci(String text) { return text.matches("[a-h][1-8][a-h][1-8][qrbn]?"); }
    @Override public void close() { if (!closed) { closed = true; channel.close(); } }

    private static final class ProcessChannel implements Channel {
        private final Process process;
        private final OutputStreamWriter input;
        private final ArrayBlockingQueue<String> rows = new ArrayBlockingQueue<>(512);
        private volatile boolean ended;
        private boolean stopping;
        private volatile IOException error;
        ProcessChannel(Process process) {
            this.process = process; input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
            Thread reader = new Thread(() -> {
                try (var output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String row;
                    while ((row = output.readLine()) != null) {
                        if (row.length() > 65536 || !rows.offer(row)) throw new IOException("Stockfish output overflow");
                    }
                } catch (IOException failure) { error = failure; }
                finally { ended = true; }
            }, "stockfish-uci-output");
            reader.setDaemon(true); reader.start();
        }
        @Override public void send(String command) throws IOException { input.write(command + "\n"); input.flush(); }
        @Override public String receive(long timeout) throws IOException, InterruptedException {
            String row = rows.poll(timeout, TimeUnit.MILLISECONDS);
            if (row != null) return row;
            if (error != null) throw error;
            if (ended) throw new IOException("Stockfish closed its output");
            return null;
        }
        @Override public synchronized void close() {
            if (stopping) return;
            stopping = true;
            Thread quitter = new Thread(() -> {
                try { input.write("stop\nquit\n"); input.flush(); } catch (IOException ignored) { }
            }, "stockfish-uci-quit");
            quitter.setDaemon(true); quitter.start();
            Thread reaper = new Thread(() -> {
                try {
                    if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) {
                        process.destroy();
                        if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) process.destroyForcibly();
                    }
                } catch (InterruptedException interrupted) { process.destroy(); Thread.currentThread().interrupt(); }
                finally { try { input.close(); } catch (IOException ignored) { } }
            }, "stockfish-uci-close");
            reaper.setDaemon(true); reaper.start();
        }
    }
}
