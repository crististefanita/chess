package ro.atf.chess;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.function.ToIntFunction;

/** Runtime adapter for the measured A engine. No test resource or experimental weights are consulted. */
final class HardMaster {
    record Report(HardCandidateSearch.Result result, HardMateCertificate.Proof certificate) {
        String certificateReceipt() {
            return certificate == null ? "NOT_RUN" : certificate.status() + ":nodes=" + certificate.nodes()
                    + ":generated=" + certificate.generated();
        }
    }
    record ObservedDecision(ChessMatch.BotDecision decision, Report report) {
        ObservedDecision {
            if (report == null) {
                if (decision == null || decision.move() == null || !"BOOK".equals(decision.stop()) || decision.depth() != 0 || decision.searchedNodes() != 0
                        || decision.accounting() != ChessMatch.NodeAccounting.TOTAL || !"NOT_RUN".equals(decision.certificate())) {
                    throw new IllegalArgumentException("Only an unsearched declared book action may omit the report");
                }
            } else {
                var result = report.result();
                if (result == null) throw new IllegalArgumentException("A search observation needs an actual result");
                boolean playable = result.hasCompletedIteration() && result.bound() == HardCandidateSearch.Bound.EXACT
                        && !result.line().isEmpty() && result.completedDepth() > 0 && result.nodes() >= 0
                        && result.stop() != null && result.stop() != HardCandidateSearch.Stop.CANCELLED;
                if (decision == null && playable) throw new IllegalArgumentException("A completed playable report cannot lose its action");
                if (decision != null && (!playable || !java.util.Objects.equals(decision.move(), result.line().get(0))
                        || decision.depth() != result.completedDepth() || decision.searchedNodes() != result.nodes()
                        || decision.accounting() != ChessMatch.NodeAccounting.TOTAL
                        || !result.stop().name().equals(decision.stop()) || !report.certificateReceipt().equals(decision.certificate()))) {
                    throw new IllegalArgumentException("The observed action must match its one actual complete search report");
                }
            }
        }
    }
    private HardMaster() { }
    static boolean usesA() { return HardMasterPolicy.RUNTIME == HardMasterPolicy.Engine.A; }

    static HardCandidateSearch.Context context(ChessMatch game) {
        var history = new ArrayList<ChessPosition.SearchKey>();
        for (int ply = Math.max(0, game.ply() - game.reversiblePlies()); ply <= game.ply(); ply++) {
            history.add(game.positionAt(ply).searchKey());
        }
        return new HardCandidateSearch.Context(game.position(), game.reversiblePlies(), history);
    }

    static ChessMatch.BotDecision decide(ChessMatch game) {
        return decide(game, HardMasterPolicy.CURRENT);
    }

    static ChessMatch.BotDecision decide(ChessMatch game, HardMasterPolicy policy) {
        return decide(game, policy, true);
    }

    static ChessMatch.BotDecision decide(ChessMatch game, HardMasterPolicy policy, boolean useBook) {
        var observed = decideObserved(game, policy, useBook);
        if (observed.decision() == null) {
            var result = observed.report().result();
            throw new IllegalStateException("Master has no completed decision: " + result.stop() + " nodes=" + result.nodes());
        }
        return observed.decision();
    }

    static ObservedDecision decideObserved(ChessMatch game, HardMasterPolicy policy) {
        return decideObserved(game, policy, true);
    }

    /** The action and its actual PV/work come from this one calculation. */
    static ObservedDecision decideObserved(ChessMatch game, HardMasterPolicy policy, boolean useBook) {
        if (game.result() != ChessMatch.Result.IN_PROGRESS) throw new IllegalStateException("The match has ended");
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Master search cancelled");
        var book = useBook ? game.hardBookMove() : null;
        if (book != null) return new ObservedDecision(new ChessMatch.BotDecision(book, 0, 0,
                ChessMatch.NodeAccounting.TOTAL, policy.id(), "BOOK"), null);
        var report = search(game, policy, policy::evaluate, false);
        var result = report.result();
        if (!result.hasCompletedIteration() || result.line().isEmpty()) return new ObservedDecision(null, report);
        return new ObservedDecision(new ChessMatch.BotDecision(result.line().get(0), result.completedDepth(), Math.toIntExact(result.nodes()),
                ChessMatch.NodeAccounting.TOTAL, policy.id(), result.stop().name(), report.certificateReceipt()), report);
    }
    /** The same search with its full evidence, without the repertoire shortcut. */
    static HardCandidateSearch.Result analyze(ChessMatch game, HardMasterPolicy policy) {
        return search(game, policy, policy::evaluate, false).result();
    }

    /** The certificate and core have separate allowances; an inconclusive proof never steals core depth. */
    static Report search(ChessMatch game, HardMasterPolicy policy, ToIntFunction<ChessPosition> evaluator, boolean frontier) {
        return search(game, policy, evaluator, frontier, policy.rootPv(), policy.pvs(), policy.moveHints(), policy.cutoffOrdering());
    }

    static Report search(ChessMatch game, HardMasterPolicy policy, ToIntFunction<ChessPosition> evaluator, boolean frontier, boolean rootPv) {
        return search(game, policy, evaluator, frontier, rootPv, false);
    }

    static Report search(ChessMatch game, HardMasterPolicy policy, ToIntFunction<ChessPosition> evaluator,
                         boolean frontier, boolean rootPv, boolean pvs) {
        return search(game, policy, evaluator, frontier, rootPv, pvs, false);
    }

    static Report search(ChessMatch game, HardMasterPolicy policy, ToIntFunction<ChessPosition> evaluator,
                         boolean frontier, boolean rootPv, boolean pvs, boolean moveHints) {
        return search(game, policy, evaluator, frontier, rootPv, pvs, moveHints, false);
    }

    static Report search(ChessMatch game, HardMasterPolicy policy, ToIntFunction<ChessPosition> evaluator,
                         boolean frontier, boolean rootPv, boolean pvs, boolean moveHints, boolean cutoffOrdering) {
        return search(game, policy, evaluator, frontier, rootPv, pvs, moveHints, cutoffOrdering, 0);
    }

    static Report search(ChessMatch game, HardMasterPolicy policy, ToIntFunction<ChessPosition> evaluator,
                         boolean frontier, boolean rootPv, boolean pvs, boolean moveHints, boolean cutoffOrdering, int checkExtensions) {
        return search(game, policy, evaluator, frontier, rootPv, pvs, moveHints, cutoffOrdering, checkExtensions, policy.quietTactics().mode());
    }

    static Report search(ChessMatch game, HardMasterPolicy policy, ToIntFunction<ChessPosition> evaluator,
                         boolean frontier, boolean rootPv, boolean pvs, boolean moveHints, boolean cutoffOrdering,
                         int checkExtensions, int quietChecks) {
        if (policy.engine() != HardMasterPolicy.Engine.A) throw new IllegalArgumentException("The A adapter requires an A policy");
        if (game.result() != ChessMatch.Result.IN_PROGRESS) throw new IllegalStateException("The match has ended");
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Master search cancelled");
        var settings = policy.certificate();
        var proof = settings.enabled() ? HardMateCertificate.prove(game, settings.maxPlies(), settings.nodes()) : null;
        if (proof != null && proof.status() == HardMateCertificate.Status.PROVED) {
            return new Report(new HardCandidateSearch.Result(HardCandidateSearch.MATE - proof.line().size(),
                    HardCandidateSearch.Bound.EXACT, proof.line(), proof.line().size(), proof.nodes(), 0,
                    HardCandidateSearch.Stop.MATE_CERTIFICATE), proof);
        }
        var search = new HardCandidateSearch(evaluator, HardCandidateSearch.DrawPolicy.AUTOMATIC_ONLY, frontier, rootPv, pvs, moveHints, cutoffOrdering, checkExtensions, quietChecks);
        var result = search.iterativeWithForcingReserve(context(game), policy.limits(), () -> false, policy.forcingReserve());
        if (result.stop() == HardCandidateSearch.Stop.CANCELLED) throw new CancellationException("Master search cancelled");
        long certificateNodes = proof == null ? 0 : proof.nodes();
        return new Report(new HardCandidateSearch.Result(result.score(), result.bound(), result.line(), result.completedDepth(),
                result.nodes() + certificateNodes, result.tableHits(), result.moveHintHits(), result.stop()), proof);
    }
}
