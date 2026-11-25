package ro.atf.chess;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Coalesces rapid moves into one background analysis of the latest durable checkpoint. */
public final class ReviewCollector implements AutoCloseable {
    public record Request(String id, ChessMatch match) { }
    private final Executor executor;
    private final Consumer<Request> collect;
    private final Consumer<RuntimeException> failed;
    private Request pending;
    private String last;
    private String inFlight;
    private boolean running;
    private boolean closed;

    public ReviewCollector(Executor executor, Consumer<Request> collect, Consumer<RuntimeException> failed) {
        this.executor = executor; this.collect = collect; this.failed = failed;
    }

    public synchronized void submit(String id, ChessMatch match) {
        String key = id + "|" + match.checkpoint();
        if (closed || id == null || match.ply() == 0 || key.equals(last) || key.equals(inFlight)) return;
        if (pending != null && pending.id().equals(id) && pending.match().checkpoint().equals(match.checkpoint())) return;
        pending = new Request(id, match);
        if (!running) {
            running = true;
            try { executor.execute(this::drain); }
            catch (RuntimeException rejected) { running = false; pending = null; failed.accept(rejected); }
        }
    }

    private void drain() {
        for (;;) {
            Request next;
            synchronized (this) {
                next = closed ? null : pending; pending = null;
                if (next == null) { running = false; return; }
                inFlight = next.id() + "|" + next.match().checkpoint();
            }
            try {
                collect.accept(next);
                synchronized (this) { last = inFlight; }
            } catch (RuntimeException failure) {
                synchronized (this) {
                    if (!closed && !(failure instanceof java.util.concurrent.CancellationException)) failed.accept(failure);
                }
            } finally { synchronized (this) { inFlight = null; } }
        }
    }

    @Override public synchronized void close() { closed = true; pending = null; }
}
