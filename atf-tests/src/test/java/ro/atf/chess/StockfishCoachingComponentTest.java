package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class StockfishCoachingComponentTest {
    static final ExternalEngineAnalysis.Provider PROVIDER = new ExternalEngineAnalysis.Provider(
            "stockfish-19-android-x86_64", "19", "a".repeat(64), "edb0d9db6731067ec50ce619ff372b463bc4dd5d",
            "{\"threads\":1,\"hashMb\":32,\"nodes\":200000,\"limitStrength\":false}");

    @Test void allSurfacesUseFullStrengthWithTheCompletePrefix(Checks c) {
        var channel = new Fake(); var coaching = new StockfishCoaching(() -> new StockfishTransport(channel, PROVIDER, 200000, 3));
        var match = ChessMatch.restore("2|analysis|e2e4 e7e5 g1f3");
        var hint = coaching.hint(match, 3);
        c.same("Source is Stockfish rather than a Native hint", PROVIDER, hint.external().orElseThrow().provider());
        c.same("All played moves reach UCI", true, channel.commands.contains("position startpos moves e2e4 e7e5 g1f3"));
        for (String command : List.of("setoption name Threads value 1", "setoption name Hash value 32",
                "setoption name Skill Level value 20", "setoption name UCI_LimitStrength value false", "setoption name UCI_ShowWDL value true"))
            c.same("Actual command: " + command, true, channel.commands.contains(command));
        c.same("Node request is explicit; actual work stays in evidence", true, channel.commands.contains("go nodes 200000"));
        c.same("UCI transcript is auditable", true, channel.lastSearch != null);
        coaching.close(); c.same("Owner releases its transport", true, channel.closed);
    }

    @Test void completedRoundsDoNotMixWithPartialOrBoundedHorizons(Checks c) {
        var fake = new Fake(); fake.partial = true;
        var engine = new StockfishTransport(fake, PROVIDER, 200000, 3);
        var result = engine.search(ChessMatch.start(), 0, null);
        c.same("An interrupted deeper iteration retains the latest complete common depth", 8, result.batch().depth());
        c.same("All three complete recommendations survive a partial rank-one update", 3, result.batch().lines().size());
        c.same("Complete earlier MultiPV remains intact for review", 3, result.completed().get(8).lines().size());
        c.same("A bounded root is never exact evidence", null, StockfishTransport.info("info depth 9 score cp 30 lowerbound nodes 20 time 1 wdl 300 400 300 pv e2e4"));
        c.rejects("Missing WDL cannot be presented as a measured score", IllegalArgumentException.class,
                () -> StockfishTransport.info("info depth 8 score cp 30 nodes 20 time 1 pv e2e4"));
        engine.close();
        var duplicate = new Fake(); duplicate.duplicateRoots = true;
        try (var isolated = new StockfishTransport(duplicate, PROVIDER, 200000, 3)) {
            var retained = isolated.search(ChessMatch.start(), 0, null);
            c.same("Repeated roots cannot certify a deeper three-choice cohort", false, retained.completed().containsKey(9));
            c.same("A duplicate tail retains the previous distinct completed horizon", 8, retained.batch().depth());
            c.same("All retained alternatives are distinct", 3L, retained.batch().lines().stream().map(ExternalEngineAnalysis.Line::root).distinct().count());
        }
    }

    @Test void pendingStockfishCannotBlockOrAlterAnyNativeOpponentDecision(Checks c) throws Exception {
        for (var level : MatchSetup.Level.values()) {
            var match = ChessMatch.start(new MatchSetup(ChessPosition.Side.WHITE, level, true), 1)
                    .play(ChessPosition.Move.fromUci("e2e4"));
            var expected = match.botDecision();
            var started = new CountDownLatch(1);
            var fake = new Fake(); fake.block = started;
            try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
                 var coaching = new StockfishCoaching(() -> new StockfishTransport(fake, PROVIDER, 200000, 3))) {
                var analysis = workers.submit(() -> coaching.hint(match, match.ply()));
                c.same(level + " Stockfish reaches a stalled search", true, started.await(3, TimeUnit.SECONDS));
                var opponent = workers.submit((java.util.concurrent.Callable<ChessMatch.BotDecision>) match::botDecision);
                c.same(level + " opponent completes while Stockfish is still pending", expected,
                        opponent.get(3, TimeUnit.SECONDS));
                c.same(level + " analysis remains independent and unfinished", false, analysis.isDone());
                coaching.close();
                c.rejects(level + " cancelled analysis cannot publish a hint", java.util.concurrent.ExecutionException.class,
                        () -> analysis.get(3, TimeUnit.SECONDS));
                c.same(level + " cancellation preserves native move, horizon and cost", expected, match.botDecision());
            }
        }
    }

    @Test void reviewMatchesFreeAndPlayedAtACommonCompletedDepth(Checks c) {
        var fake = new Fake(); fake.partial = true; var audited = new ArrayList<String>();
        try (var coaching = new StockfishCoaching(() -> new StockfishTransport(fake, PROVIDER, 200000, 3),
                (phase, result) -> audited.add(phase + ":" + result.batch().context().rootFen()))) {
            var match = ChessMatch.restore("2|analysis|e2e4 e7e5");
            var review = coaching.review(match, false, ignored -> { });
            c.same("Both played plies have external observations", 2, review.external().turns().size());
            for (var turn : review.external().turns()) {
                c.same("No scores at different horizons are compared", turn.free().depth(), turn.played().depth());
                c.same("Completed depth is retained", 8, turn.free().depth());
                c.same("Forced root is the actual move", match.moveAt(turn.ply()), turn.played().best().root());
            }
            c.same("Each free/played search is audited", 4, audited.size());
            c.same("Manual analysis does not invent human or bot Elo", true, review.white().rating().isEmpty() && review.black().rating().isEmpty());
            c.same("Stored external review survives codec round trip", review.external().json(),
                    GameReviewCodec.decodeLine(match, GameReviewCodec.encode(match, review), GameReviewCodec.checkpointDigest(match)).external().json());
        }
    }

    @Test void cachesOnlyTheExactFullPrefixAndRevalidatesNewCheckpoint(Checks c) {
        var fake = new Fake(); var audit = new ArrayList<String>();
        try (var coaching = new StockfishCoaching(() -> new StockfishTransport(fake, PROVIDER, 200000, 3), (phase, result) -> audit.add(phase))) {
            var first = ChessMatch.restore("2|analysis|e2e4"); var longer = ChessMatch.restore("2|analysis|e2e4 e7e5");
            coaching.hint(first, 1); int searches = fake.searches;
            var reused = coaching.hint(longer, 1).external().orElseThrow();
            c.same("Appending a future move need not repeat the same prefix", searches, fake.searches);
            c.same("Reuse is rebound to the current checkpoint", ExternalEngineAnalysis.context(longer, 1).contextKey(), reused.context().contextKey());
            c.same("Reuse is explicitly identified in the audit", true, audit.contains("reuse:free"));
            c.same("A cache hit cannot masquerade as another observed search", 1L,
                    audit.stream().filter("free"::equals).count());
            coaching.hint(ChessMatch.restore("2|analysis|g1f3 g8f6 f3g1 f6g8"), 4);
            c.same("Repetition history cannot hit an unrelated starting position", searches + 1, fake.searches);
        }
    }

    @Test void engineFailuresNeverFallBackToNativeSearch(Checks c) {
        var fake = new Fake(); fake.badPv = true;
        try (var coaching = new StockfishCoaching(() -> new StockfishTransport(fake, PROVIDER, 200000, 3))) {
            c.rejects("Illegal PV fails closed instead of making a Native hint", IllegalArgumentException.class,
                    () -> coaching.hint(ChessMatch.start(), 0));
            c.same("Invalid engine output destroys that session", true, fake.closed);
        }
        var foreign = new Fake(); foreign.engineName = "OtherEngine 19";
        try (var engine = new StockfishTransport(foreign, PROVIDER, 200000, 3)) {
            c.rejects("A banner from another engine cannot impersonate Stockfish", IllegalArgumentException.class,
                    () -> engine.search(ChessMatch.start(), 0, null));
        }
        c.rejects("Provider policy cannot claim a different node budget", IllegalArgumentException.class,
                () -> new StockfishTransport(new Fake(), PROVIDER, 88000, 3));
    }

    @Test void longerLegalPrincipalVariationsArePreservedWithoutInventedProof(Checks c) {
        var fake = new Fake(); fake.longPv = true;
        try (var coaching = new StockfishCoaching(() -> new StockfishTransport(fake, PROVIDER, 200000, 3))) {
            var hint = coaching.hint(ChessMatch.start(), 0);
            c.same("All ten observed plies remain playable", 10, hint.external().orElseThrow().best().pv().size());
            c.same("Engine depth is not an exhaustive tactical proof", true, hint.proof().isEmpty());
            c.same("No Native search receipt is fabricated", ExternalEngineAnalysis.PROOF_STATUS,
                    hint.external().orElseThrow().provider().json().contains(ExternalEngineAnalysis.PROOF_STATUS) ? ExternalEngineAnalysis.PROOF_STATUS : "missing");
        }
    }

    @Test void closeCanInterruptAnActiveSearchWithoutWaitingForItsMonitor(Checks c) throws Exception {
        var started = new CountDownLatch(1); var ended = new CountDownLatch(1); var outcome = new AtomicReference<Throwable>();
        var fake = new Fake(); fake.block = started;
        var engine = new StockfishTransport(fake, PROVIDER, 200000, 3);
        Thread search = new Thread(() -> { try { engine.search(ChessMatch.start(), 0, null); } catch (Throwable e) { outcome.set(e); } finally { ended.countDown(); } });
        search.start(); c.same("Native search really started", true, started.await(3, TimeUnit.SECONDS));
        engine.close();
        c.same("Close wakes the blocked reader", true, ended.await(3, TimeUnit.SECONDS));
        c.same("Interrupted work cannot return a scored hint", true, outcome.get() instanceof IllegalStateException);
    }

    @Test void boundedTopStartsANewCohortAndCannotReuseAnOlderExactTop(Checks c) {
        var fake = new Fake(); fake.mixedBoundRound = true;
        try (var engine = new StockfishTransport(fake, PROVIDER, 200000, 3)) {
            var result = engine.search(ChessMatch.start(), 0, null);
            c.same("The complete earlier round remains usable", true, result.completed().containsKey(8));
            c.same("Old exact top plus tails after a bounded top is not a complete round", false, result.completed().containsKey(9));
            c.same("A bounded top cannot replace the earlier complete three-root result", 3, result.batch().lines().size());
            c.same("The published hint keeps the common completed horizon", 8, result.batch().depth());
        }
    }

    @Test void closeDuringFactoryCreationCannotLeakOrPublishANewEngine(Checks c) throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var done = new CountDownLatch(1);
        var fake = new Fake(); var outcome = new AtomicReference<Throwable>();
        var coaching = new StockfishCoaching(() -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
            return new StockfishTransport(fake, PROVIDER, 200000, 3);
        });
        Thread job = new Thread(() -> { try { coaching.hint(ChessMatch.start(), 0); } catch (Throwable failure) { outcome.set(failure); } finally { done.countDown(); } });
        job.start();
        c.same("Factory really holds an in-flight startup", true, entered.await(3, TimeUnit.SECONDS));
        coaching.close(); release.countDown();
        c.same("The canceled factory returns without leaking work", true, done.await(3, TimeUnit.SECONDS));
        c.same("A created engine after close is itself closed", true, fake.closed);
        c.same("No hint or null-pointer exception is produced", true, outcome.get() instanceof java.util.concurrent.CancellationException);
        c.same("Canceled startup never starts a search", 0, fake.searches);
    }

    @Test void missingCommonHorizonRejectsTheReviewInsteadOfComparingDifferentDepths(Checks c) {
        var fake = new Fake(); fake.forcedDifferentDepth = true;
        try (var coaching = new StockfishCoaching(() -> new StockfishTransport(fake, PROVIDER, 200000, 3))) {
            c.rejects("Free depth eight and forced depth nine do not make a graded turn", IllegalStateException.class,
                    () -> coaching.review(ChessMatch.restore("2|analysis|e2e4"), false, ignored -> { }));
        }
    }

    @Test void aBlockedProcessWriterCannotBlockCloseOrItsIndependentKillDeadline(Checks c) throws Exception {
        var process = new BlockedInputProcess(); var done = new CountDownLatch(1);
        var engine = StockfishTransport.process(process, PROVIDER, 200000, 3);
        Thread search = new Thread(() -> { try { engine.search(ChessMatch.start(), 0, null); } catch (RuntimeException expected) { } finally { done.countDown(); } });
        search.setDaemon(true); search.start();
        try {
            c.same("The actual process input write is blocked", true, process.writing.await(3, TimeUnit.SECONDS));
            var returned = new CountDownLatch(1);
            Thread close = new Thread(() -> { engine.close(); returned.countDown(); }); close.setDaemon(true); close.start();
            c.same("UI close returns while the process input is still blocked", true, returned.await(1, TimeUnit.SECONDS));
            c.same("Independent reaper terminates the unresponsive process", true, process.terminated.await(5, TimeUnit.SECONDS));
            c.same("Blocked search releases without publishing a score", true, done.await(3, TimeUnit.SECONDS));
        } finally { process.destroy(); }
    }

    @Test void closedOwnerCannotRestartBetweenReviewPliesOrServeCachedHints(Checks c) {
        var fake = new Fake(); int[] created = {0};
        var owner = new StockfishCoaching(() -> { created[0]++; return new StockfishTransport(fake, PROVIDER, 200000, 3); });
        var match = ChessMatch.restore("2|analysis|e2e4 e7e5");
        c.rejects("Closing after the first reviewed ply cancels the whole operation", java.util.concurrent.CancellationException.class,
                () -> owner.review(match, false, ply -> owner.close()));
        c.same("Only the first free/played pair was searched", 2, fake.searches);
        c.same("Closed owner never creates a replacement process", 1, created[0]);
        c.rejects("Even a complete cached result cannot reopen a closed owner", java.util.concurrent.CancellationException.class,
                () -> owner.hint(match, 0));
    }

    private static final class BlockedInputProcess extends Process {
        final CountDownLatch writing = new CountDownLatch(1), terminated = new CountDownLatch(1);
        private final java.io.OutputStream input = new java.io.OutputStream() {
            @Override public void write(int value) throws IOException {
                writing.countDown();
                try { terminated.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                throw new IOException("process no longer reads stdin");
            }
        };
        @Override public java.io.OutputStream getOutputStream() { return input; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(new byte[0]); }
        @Override public java.io.InputStream getErrorStream() { return new java.io.ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() throws InterruptedException { terminated.await(); return 1; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException { return terminated.await(timeout, unit); }
        @Override public int exitValue() { if (terminated.getCount() > 0) throw new IllegalThreadStateException(); return 1; }
        @Override public void destroy() { terminated.countDown(); }
        @Override public Process destroyForcibly() { destroy(); return this; }
    }

    private static final class Fake implements StockfishTransport.Channel {
        final ArrayDeque<String> rows = new ArrayDeque<>(); final List<String> commands = new ArrayList<>();
        String position = "", engineName = "Stockfish 19", lastSearch;
        boolean partial, duplicateRoots, badPv, longPv, mixedBoundRound, forcedDifferentDepth; volatile boolean closed; int multi = 3, searches; CountDownLatch block;
        @Override public void send(String command) {
            commands.add(command);
            if (command.equals("uci")) {
                rows.add("id name " + engineName);
                for (String option : List.of("Threads", "Hash", "MultiPV", "Skill Level", "UCI_LimitStrength", "UCI_ShowWDL", "Ponder", "EvalFile"))
                    rows.add("option name " + option + " type string default");
                rows.add("uciok");
            } else if (command.equals("isready")) rows.add("readyok");
            else if (command.startsWith("position ")) position = command;
            else if (command.startsWith("setoption name MultiPV value ")) multi = Integer.parseInt(command.substring(command.lastIndexOf(' ') + 1));
            else if (command.startsWith("go ")) {
                searches++; lastSearch = command;
                if (block != null) { block.countDown(); return; }
                String moves = position.contains(" moves ") ? position.split(" moves ", 2)[1] : "";
                var board = ChessMatch.restore("2|analysis|" + moves).position();
                var choices = board.legalMoves(); String forced = command.contains(" searchmoves ") ? command.split(" searchmoves ", 2)[1] : null;
                String best = forced != null ? forced : choices.get(0).uci();
                if (longPv && forced == null) best = "e2e4";
                for (int rank = 1; rank <= multi; rank++) {
                    String pv = forced != null ? forced : rank == 1 ? best : choices.get(rank - 1).uci();
                    if (longPv && rank == 1) pv = "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7";
                    if (badPv) pv = "e2e5";
                    rows.add(info(forcedDifferentDepth && forced != null ? 9 : 8, rank, pv));
                }
                if (partial) rows.add(info(9, 1, best));
                if (duplicateRoots) {
                    rows.add(info(9, 1, best));
                    rows.add(info(9, 2, choices.get(1).uci()));
                    rows.add(info(9, 3, choices.get(1).uci()));
                }
                if (mixedBoundRound) {
                    rows.add(info(9, 1, best));
                    rows.add(info(9, 1, choices.get(3).uci()).replace(" score cp 39 ", " score cp 39 lowerbound "));
                    rows.add(info(9, 2, choices.get(1).uci())); rows.add(info(9, 3, choices.get(2).uci()));
                }
                rows.add("bestmove " + best);
            }
        }
        private String info(int depth, int rank, String pv) {
            return "info depth " + depth + " seldepth 12 multipv " + rank + " score cp " + (40 - rank) + " wdl 400 400 200 nodes 200123 time 12 pv " + pv;
        }
        @Override public String receive(long timeout) throws IOException, InterruptedException {
            if (closed) throw new IOException("closed");
            if (!rows.isEmpty()) return rows.remove();
            Thread.sleep(Math.min(timeout, 25)); return null;
        }
        @Override public void close() { closed = true; }
    }
}
