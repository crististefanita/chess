package ro.atf.chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** Proves short mates with a cap on visited nodes and respects automatic draws in a match. */
final class HardMateCertificate {
    enum Status { PROVED, NO_MATE, BUDGET_EXHAUSTED }

    record Proof(Status status, List<Move> line, int nodes, int generated) { }

    private record Key(ChessPosition.SearchKey position, int plies) { }
    private record Candidate(Move move, ChessPosition position) { }

    private static final class Exhausted extends RuntimeException { }

    private final Side attacker;
    private final int limit;
    // A null entry records a disproved mate at this horizon.
    private final Map<Key, List<Move>> cache = new HashMap<>();
    private int nodes;
    private int generated;

    private HardMateCertificate(Side attacker, int limit) {
        this.attacker = attacker;
        this.limit = limit;
    }

    static Proof prove(ChessPosition position, int maxPlies, int limit) {
        return prove(position, null, maxPlies, limit);
    }

    static Proof prove(ChessMatch match, int maxPlies, int limit) {
        return prove(match.position(), match, maxPlies, limit);
    }

    private static Proof prove(ChessPosition position, ChessMatch match, int maxPlies, int limit) {
        if (maxPlies < 1 || maxPlies > MatchAiPolicy.MAX_PLIES || maxPlies % 2 == 0 || limit < 1) {
            throw new IllegalArgumentException("Invalid forced mate search limits");
        }
        if (match != null && match.result() != ChessMatch.Result.IN_PROGRESS) return new Proof(Status.NO_MATE, HardCollections.list(), 0, 0);
        var search = new HardMateCertificate(position.turn(), limit);
        var tracked = match != null && match.mayReachAutomaticDrawIn(maxPlies) ? match : null;
        try {
            for (int plies = 1; plies <= maxPlies; plies += 2) {
                var line = search.visit(position, tracked, plies);
                if (line != null && !line.isEmpty()) return new Proof(Status.PROVED, line, search.nodes, search.generated);
            }
            return new Proof(Status.NO_MATE, HardCollections.list(), search.nodes, search.generated);
        } catch (Exhausted exhausted) {
            return new Proof(Status.BUDGET_EXHAUSTED, HardCollections.list(), search.nodes, search.generated);
        }
    }

    private List<Move> visit(ChessPosition position, ChessMatch match, int plies) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        if (nodes == limit) throw new Exhausted();
        nodes++;
        HardSearchExperiment.visit();
        if (match != null && match.result() != ChessMatch.Result.IN_PROGRESS) {
            return position.turn() != attacker && position.isCheckmate() ? HardCollections.list() : null;
        }
        if (plies == 0) return position.turn() != attacker && position.isCheckmate() ? HardCollections.list() : null;

        // An equal board may have a different draw outcome when a reversible history is active.
        var key = match == null ? new Key(position.searchKey(), plies) : null;
        if (key != null && cache.containsKey(key)) return cache.get(key);
        var moves = position.legalMoves();
        boolean attacking = position.turn() == attacker;
        List<Move> line;
        if (moves.isEmpty()) line = !attacking && position.isCheck() ? HardCollections.list() : null;
        else if (attacking) line = attack(position, match, moves, plies);
        else line = defend(position, match, moves, plies);
        if (key != null) cache.put(key, line);
        return line;
    }

    private List<Move> attack(ChessPosition position, ChessMatch match, List<Move> moves, int plies) {
        if (plies == 1) {
            for (Move move : moves) {
                var next = generate(position, move);
                if (!next.isCheck()) continue;
                var line = visit(next, advance(match, move, next), plies - 1);
                if (line != null) return prepend(move, line);
            }
            return null;
        }
        var candidates = new ArrayList<Candidate>(moves.size());
        for (Move move : moves) candidates.add(new Candidate(move, generate(position, move)));
        candidates.sort(Comparator.comparing((Candidate next) -> next.position().isCheck()).reversed());
        for (Candidate candidate : candidates) {
            var line = visit(candidate.position(), advance(match, candidate.move(), candidate.position()), plies - 1);
            if (line != null) return prepend(candidate.move(), line);
        }
        return null;
    }

    private List<Move> defend(ChessPosition position, ChessMatch match, List<Move> moves, int plies) {
        // One refutation is sufficient. A proved line shows the longest defense.
        List<Move> longest = HardCollections.list();
        for (Move move : moves) {
            var next = generate(position, move);
            var line = visit(next, advance(match, move, next), plies - 1);
            if (line == null) return null;
            if (line.size() + 1 > longest.size()) longest = prepend(move, line);
        }
        return longest;
    }

    private ChessPosition generate(ChessPosition position, Move move) {
        generated++;
        return position.playGenerated(move);
    }

    private static ChessMatch advance(ChessMatch match, Move move, ChessPosition next) {
        return match == null ? null : match.afterGenerated(move, next);
    }

    private static List<Move> prepend(Move move, List<Move> tail) {
        var line = new ArrayList<Move>(tail.size() + 1);
        line.add(move);
        line.addAll(tail);
        return HardCollections.copyList(line);
    }
}
