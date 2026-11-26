package ro.atf.chess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Stockfish-only coaching facade. Opponent difficulty never enters its search policy. */
public final class StockfishCoaching implements AutoCloseable {
    public interface Audit {
        void observed(String phase, StockfishTransport.Search result);
    }
    private final Supplier<StockfishTransport> factory;
    private final Audit audit;
    private final Object lifecycle = new Object();
    private long generation;
    private volatile boolean closed;
    private volatile StockfishTransport engine;
    private final Map<String, StockfishTransport.Search> cache = new LinkedHashMap<String, StockfishTransport.Search>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, StockfishTransport.Search> entry) { return size() > 64; }
    };

    public StockfishCoaching(Supplier<StockfishTransport> factory) { this(factory, (phase, result) -> { }); }
    public StockfishCoaching(Supplier<StockfishTransport> factory, Audit audit) { this.factory = factory; this.audit = audit; }

    public synchronized MatchHint hint(ChessMatch match, int ply) {
        requireOpen();
        var hint = MatchHint.external(match.positionAt(ply), search(match, ply, null).batch());
        requireOpen(); return hint;
    }

    public synchronized GameReview review(ChessMatch match, boolean rated, IntConsumer progress) {
        requireOpen();
        if (match.ply() == 0) throw new IllegalArgumentException("Play a move before Game Review");
        if (rated && match.result() == ChessMatch.Result.IN_PROGRESS) throw new IllegalArgumentException("Unfinished analysis is unrated");
        var turns = new ArrayList<ExternalEngineAnalysis.ReviewedTurn>();
        for (int ply = 1; ply <= match.ply(); ply++) {
            requireOpen();
            var free = search(match, ply - 1, null); var played = search(match, ply - 1, match.moveAt(ply));
            int common = 0;
            for (Integer depth : free.completed().keySet()) if (played.completed().containsKey(depth)) common = Math.max(common, depth);
            if (common == 0) throw new IllegalStateException("Stockfish has no common completed depth for this move");
            turns.add(new ExternalEngineAnalysis.ReviewedTurn(ply, free.completed().get(common), played.completed().get(common)));
            progress.accept(ply);
            requireOpen();
        }
        return GameReview.external(match, new ExternalEngineAnalysis.Review(
                ExternalEngineAnalysis.context(match, match.ply()).contextKey(), turns, rated));
    }

    private StockfishTransport.Search search(ChessMatch match, int ply, ChessPosition.Move forced) {
        requireOpen();
        var context = ExternalEngineAnalysis.context(match, ply);
        String key = context.initialFen() + "\n" + String.join(" ", context.moves()) + "\n" + context.rootFen()
                + "|" + (forced == null ? "free" : forced.uci());
        var cached = cache.get(key);
        if (cached != null) {
            var rebound = new LinkedHashMap<Integer, ExternalEngineAnalysis.Batch>();
            for (var item : cached.completed().entrySet()) rebound.put(item.getKey(), rebind(match, ply, context, item.getValue()));
            var result = new StockfishTransport.Search(rebind(match, ply, context, cached.batch()), rebound, cached.transcript(), cached.bestmove());
            audit.observed("reuse:" + (forced == null ? "free" : "played:" + forced.uci()), result);
            return result;
        }
        StockfishTransport current; long expectedGeneration;
        synchronized (lifecycle) { requireOpen(); current = engine; expectedGeneration = generation; }
        if (current == null) {
            var created = factory.get();
            synchronized (lifecycle) {
                if (generation != expectedGeneration) {
                    created.close(); throw new java.util.concurrent.CancellationException("Stockfish owner changed during startup");
                }
                engine = created; current = created;
            }
        }
        try {
            var result = current.search(match, ply, forced);
            synchronized (lifecycle) {
                if (generation != expectedGeneration || engine != current)
                    throw new java.util.concurrent.CancellationException("Stockfish owner changed during search");
            }
            audit.observed(forced == null ? "free" : "played:" + forced.uci(), result);
            cache.put(key, result); return result;
        } catch (RuntimeException failed) {
            current.close(); synchronized (lifecycle) { if (engine == current) engine = null; } throw failed;
        }
    }

    private void requireOpen() {
        if (closed || Thread.currentThread().isInterrupted())
            throw new java.util.concurrent.CancellationException("Stockfish coaching owner is closed or interrupted");
    }

    private static ExternalEngineAnalysis.Batch rebind(ChessMatch match, int ply, ExternalEngineAnalysis.Context context,
                                                       ExternalEngineAnalysis.Batch batch) {
        return ExternalEngineAnalysis.batch(match, ply,
                new ExternalEngineAnalysis.Batch(context, batch.provider(), batch.depth(), batch.lines()).json());
    }

    /** Must not wait for a long synchronized search on the UI thread. */
    @Override public void close() {
        StockfishTransport current;
        synchronized (lifecycle) { closed = true; generation++; current = engine; engine = null; }
        if (current != null) current.close();
    }
}
