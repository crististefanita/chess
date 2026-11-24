package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.HardCandidateSearch.Bound;
import ro.atf.chess.HardCandidateSearch.Context;
import ro.atf.chess.HardCandidateSearch.Limits;
import ro.atf.chess.HardCandidateSearch.Stop;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardCandidateSearchTest {
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final List<String> POSITIONS = List.of(
            START, "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
            "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2",
            "7k/P7/8/8/8/8/8/7K w - - 0 1",
            "7k/8/5KQ1/8/8/8/8/8 w - - 0 1");
    private static final ToIntFunction<ChessPosition> EVALUATION =
            position -> TacticalAnalysis.positionValue(position, position.turn());
    private static Limits limits(int q, long nodes, int entries) { return new Limits(4, q, nodes, entries); }

    @Test void fixedDepthMatchesIndependentExhaustiveMinimaxIncludingSpecialMoves(Checks check) {
        int comparisons = 0;
        long exhaustiveNodes = 0;
        long candidateNodes = 0;
        for (String fen : POSITIONS) {
            var root = Context.fromFen(fen);
            for (int depth = 1; depth <= 3; depth++) {
                var visits = new long[1];
                int expected = minimax(root, depth, 0, visits);
                var engine = new HardCandidateSearch();
                var result = engine.fixed(root, depth, limits(0, 2_000_000, 4_096),
                        -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
                check.same(fen + " H" + depth + " completes", Stop.COMPLETED, result.stop());
                check.same("Full window yields an exact score without demanding every child exact", Bound.EXACT, result.bound());
                check.same("Alpha-beta equals exhaustive minimax at identical horizon/evaluator", expected, result.score());
                check.same("The principal variation replays legally", true, legal(root.position(), result.line()));
                exhaustiveNodes += visits[0];
                candidateNodes += result.nodes();
                comparisons++;
            }
        }
        check.same("All five fixtures at three horizons were compared", 15, comparisons);
        check.same("Equal-score pruning: candidate " + candidateNodes + " vs exhaustive " + exhaustiveNodes + " visits",
                true, candidateNodes < exhaustiveNodes);
    }

    @Test void narrowBoundsCannotPoisonLaterFullWindowAndCacheOffAgrees(Checks check) {
        for (String fen : POSITIONS.subList(0, 4)) {
            var root = Context.fromFen(fen);
            int expected = minimax(root, 2, 0);
            var engine = new HardCandidateSearch();
            var upper = engine.fixed(root, 2, limits(0, 500_000, 4_096), expected + 1, expected + 2);
            check.same("Fail-low is an upper bound", Bound.UPPER, upper.bound());
            check.same("Upper bound contains the exhaustive value", true, upper.score() >= expected);
            var lower = engine.fixed(root, 2, limits(0, 500_000, 4_096), expected - 2, expected - 1);
            check.same("Fail-high is a lower bound", Bound.LOWER, lower.bound());
            check.same("Lower bound contains the exhaustive value", true, lower.score() <= expected);
            var full = engine.fixed(root, 2, limits(0, 500_000, 4_096), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            var warm = engine.fixed(root, 2, limits(0, 500_000, 4_096), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            var off = engine.fixed(root, 2, limits(0, 500_000, 0), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
            check.same("Full window after both narrow windows is still exact", Bound.EXACT, full.bound());
            check.same("Narrow windows do not alter the later exact value", expected, full.score());
            check.same("Warm exact cache preserves value", expected, warm.score());
            check.same("Warm cache has an actual hit", true, warm.tableHits() > 0);
            check.same("Disabling cache preserves value", expected, off.score());
            check.same("Disabled cache records no hit", 0L, off.tableHits());
        }
    }

    @Test void checkmatePrecedesDrawClaimsAndStalemateIsZero(Checks check) {
        var mate = Context.fromFen("7k/6Q1/5K2/8/8/8/8/8 b - - 100 1");
        var stale = Context.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        var engine = new HardCandidateSearch();
        check.same("Checkmate takes precedence at the fifty-move boundary", -HardCandidateSearch.MATE,
                engine.fixed(mate, 2, limits(0, 500, 128), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
        check.same("Stalemate has a draw score", 0,
                engine.fixed(stale, 2, limits(0, 500, 128), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
    }

    @Test void historyAndHalfmoveClockSeparateOtherwiseIdenticalCachePositions(Checks check) {
        var root = Context.fromFen(START);
        for (String move : List.of("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")) {
            root = root.play(Move.fromUci(move));
        }
        check.same("The replay reaches the initial board", Context.fromFen(START).position().searchKey(), root.position().searchKey());
        check.same("Three visits provide a draw claim", true, root.drawClaim());
        var noHistory = new Context(root.position(), root.quietPlies(), List.of(root.position().searchKey()));
        var fifty = Context.fromFen(START.replace("0 1", "100 1"));
        var engine = new HardCandidateSearch(position -> 73, HardCandidateSearch.DrawPolicy.CLAIM_AVAILABLE);
        check.same("Fresh root caches the static score", 73,
                engine.fixed(noHistory, 0, limits(0, 500, 128), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
        check.same("Same board with repetition cannot reuse the fresh score", 0,
                engine.fixed(root, 0, limits(0, 500, 128), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
        check.same("Same board with a fifty-move claim cannot reuse the fresh score", 0,
                engine.fixed(fifty, 0, limits(0, 500, 128), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
        check.same("A pawn move resets the clock", 0, fifty.play(Move.fromUci("e2e4")).quietPlies());
        var ep = Context.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 99 2").play(Move.fromUci("e5d6"));
        check.same("En passant resets the clock", 0, ep.quietPlies());
        check.same("En passant removes the captured pawn", null, ep.position().pieceAt(BoardSquare.parse("d5")));
        var automatic = new HardCandidateSearch(position -> 73);
        check.same("Default policy does not silently claim on the third occurrence", 73,
                automatic.fixed(root, 0, limits(0, 500, 128), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
        check.same("Default policy agrees with the application's automatic 75-move rule", 0,
                automatic.fixed(Context.fromFen(START.replace("0 1", "150 1")), 0,
                        limits(0, 500, 128), -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
    }

    @Test void interruptedIterationsKeepOnlyTheLastCompletedResult(Checks check) {
        var root = Context.fromFen(START);
        var early = new HardCandidateSearch().iterative(root, limits(0, 1, 128), () -> false);
        check.same("Exhaustion before H1 is explicit", Stop.NODE_LIMIT, early.stop());
        check.same("No completed iteration means no published score", null, early.score());
        check.same("No completed iteration means no fabricated PV", List.of(), early.line());
        var partial = new HardCandidateSearch().iterative(root, limits(0, 30, 128), () -> false);
        check.same("The second iteration hits its declared total cap", Stop.NODE_LIMIT, partial.stop());
        check.same("Only H1 is published", 1, partial.completedDepth());
        check.same("Published score equals independent H1", minimax(root, 1, 0), partial.score());
        check.same("The total includes all iterative-deepening work", 30L, partial.nodes());
        var calls = new AtomicInteger();
        var cancelled = new HardCandidateSearch().iterative(root, limits(0, 10_000, 128), () -> calls.incrementAndGet() > 25);
        check.same("Cancellation is distinct from the node limit", Stop.CANCELLED, cancelled.stop());
        check.same("Cancellation does not publish the unfinished depth", 1, cancelled.completedDepth());
    }

    @Test void quiescenceSeesRecaptureAndCannotPublishAnUnfinishedForcingTail(Checks check) {
        var root = Context.fromFen("6k1/8/3p4/4r3/4Q3/8/8/6K1 w - - 0 1");
        var engine = new HardCandidateSearch();
        var result = engine.fixed(root, 1, limits(8, 100_000, 4_096),
                -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("The exchange-aware search finishes", Stop.COMPLETED, result.stop());
        check.same("The queen is not exchanged for a rook and then recaptured", false,
                result.line().get(0).equals(Move.fromUci("e4e5")));
        check.same("The exchange-aware principal variation is legal", true, legal(root.position(), result.line()));
        var truncated = new HardCandidateSearch().fixed(root, 1, limits(1, 100_000, 4_096),
                -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("A forcing chain longer than the configured proof cap is explicit", Stop.QUIESCENCE_LIMIT, truncated.stop());
        check.same("An unfinished exchange is not assigned a final score", null, truncated.score());
    }

    @Test void quiescenceMatchesExhaustiveExchangeTreeAndCannotReuseAStaticLeaf(Checks check) {
        var root = Context.fromFen("6k1/8/3p4/4r3/4Q3/8/8/6K1 w - - 0 1");
        var afterCapture = root.play(Move.fromUci("e4e5"));
        var engine = new HardCandidateSearch();
        var staticLeaf = engine.fixed(afterCapture, 0, limits(0, 100_000, 4_096),
                -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        var settled = engine.fixed(afterCapture, 0, limits(8, 100_000, 4_096),
                -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("A static leaf records its own evaluator value", EVALUATION.applyAsInt(afterCapture.position()), staticLeaf.score());
        check.same("The legal recapture changes the static result", true, settled.score() > staticLeaf.score());
        check.same("The exchange-aware value equals independent exhaustive quiescence",
                exhaustiveQuiescence(afterCapture, 8, 0), settled.score());
        check.same("The recapture is retained as evidence", Move.fromUci("d6e5"), settled.line().get(0));
        var staticAgain = engine.fixed(afterCapture, 0, limits(0, 100_000, 4_096),
                -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT);
        check.same("Quiescence and static leaves have separate cache identity", staticLeaf.score(), staticAgain.score());
    }

    @Test void deadMaterialIsDrawAndInvalidRequestsAreRejected(Checks check) {
        var engine = new HardCandidateSearch(position -> 73);
        for (String fen : List.of("7k/8/8/8/8/8/8/K7 w - - 0 1", "7k/8/8/8/8/8/8/KN6 w - - 0 1")) {
            check.same("Dead material is drawn before evaluation", 0,
                    engine.fixed(Context.fromFen(fen), 1, limits(0, 100, 128),
                            -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
        }
        check.rejects("A zero node budget is rejected", IllegalArgumentException.class, () -> new Limits(3, 0, 0, 128));
        check.rejects("An inverted window is rejected", IllegalArgumentException.class,
                () -> engine.fixed(Context.fromFen(START), 1, limits(0, 500, 128), 1, 0));
        check.rejects("History must actually end at the searched position", IllegalArgumentException.class,
                () -> new Context(ChessPosition.initial(), 0, List.of()));
        check.rejects("Static evaluation cannot impersonate a mate score", IllegalArgumentException.class,
                () -> new HardCandidateSearch(position -> HardCandidateSearch.MATE)
                        .fixed(Context.fromFen(START), 0, limits(0, 500, 128),
                                -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT));
    }

    @Test void defaultDrawPolicyMatchesLiveAutomaticThresholds(Checks check) {
        var engine = new HardCandidateSearch(position -> 73);
        var at50 = Context.fromFen(START.replace("0 1", "100 1"));
        var at75 = Context.fromFen(START.replace("0 1", "150 1"));
        check.same("A claim at fifty moves is not silently taken by the default candidate", 73,
                engine.fixed(at50, 0, limits(0, 1_000, 128),
                        -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
        check.same("The automatic seventy-five-move draw is respected", 0,
                engine.fixed(at75, 0, limits(0, 1_000, 128),
                        -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
        var repeated = Context.fromFen(START);
        for (int round = 0; round < 4; round++) {
            for (String move : List.of("g1f3", "g8f6", "f3g1", "f6g8")) repeated = repeated.play(Move.fromUci(move));
            check.same("Fivefold is automatic, threefold remains a claim at round " + round,
                    round == 3 ? 0 : 73, engine.fixed(repeated, 0, limits(0, 1_000, 128),
                            -HardCandidateSearch.LIMIT, HardCandidateSearch.LIMIT).score());
        }
    }

    // Independent exhaustive recurrence: no alpha-beta, TT, candidate terminal helper or ordering.
    private static int minimax(Context context, int left, int ply) {
        return minimax(context, left, ply, new long[1]);
    }

    private static int minimax(Context context, int left, int ply, long[] visits) {
        visits[0]++;
        var position = context.position();
        var moves = position.legalMoves();
        if (moves.isEmpty()) return position.isCheck() ? -HardCandidateSearch.MATE + ply : 0;
        if (context.quietPlies() >= 150 || context.history().stream().filter(position.searchKey()::equals).count() >= 5
                || ChessMatch.fromPosition(position).result() == ChessMatch.Result.MATERIAL_DRAW) return 0;
        if (left == 0) return EVALUATION.applyAsInt(position);
        int best = -HardCandidateSearch.LIMIT;
        for (Move move : moves) best = Math.max(best, -minimax(context.play(move), left - 1, ply + 1, visits));
        return best;
    }

    private static boolean legal(ChessPosition position, List<Move> line) {
        for (Move move : line) {
            if (!position.legalMoves().contains(move)) return false;
            position = position.play(move);
        }
        return true;
    }

    private static int exhaustiveQuiescence(Context context, int left, int ply) {
        var position = context.position();
        var legal = position.legalMoves();
        if (legal.isEmpty()) return position.isCheck() ? -HardCandidateSearch.MATE + ply : 0;
        if (context.automaticDraw() || ChessMatch.fromPosition(position).result() == ChessMatch.Result.MATERIAL_DRAW) return 0;
        boolean checked = position.isCheck();
        var continuations = checked ? legal : position.legalCapturesAndPromotions();
        int best = checked ? -HardCandidateSearch.LIMIT : EVALUATION.applyAsInt(position);
        if (!continuations.isEmpty() && left == 0) throw new IllegalStateException("Fixture has an unresolved forcing tail");
        for (Move move : continuations) {
            best = Math.max(best, -exhaustiveQuiescence(context.play(move), left - 1, ply + 1));
        }
        return best;
    }
}
