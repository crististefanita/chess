package ro.atf.chess;

import ro.atf.chess.ChessMatch.Result;
import static ro.atf.chess.WebBoardState.move;
import static ro.atf.chess.WebBoardState.quote;

/** Analyzes one displayed match position without access to the match ledger. */
final class WebMatchAnalysis {
    private final WebHintReplay replay = new WebHintReplay();
    private final MatchAnalysisCache cache = new MatchAnalysisCache();
    private Frame frame;
    private ExternalEngineAnalysis.Context externalContext;

    String prepareExternal(String checkpoint, int ply, int revision) {
        if (frame != null && revision < frame.revision()) return prepared(state("STALE"), null);
        var match = ChessMatch.restore(checkpoint);
        var context = ExternalEngineAnalysis.context(match, ply);
        boolean same = frame != null && revision == frame.revision() && checkpoint.equals(frame.checkpoint()) && ply == frame.ply()
                && externalContext != null && context.contextKey().equals(externalContext.contextKey());
        if (!same) {
            String evaluation = ply == match.ply() && match.result() != Result.IN_PROGRESS
                    ? WebBoardState.terminalEvaluation(match.result()) : "null";
            frame = new Frame(checkpoint, ply, revision, match.positionAt(ply), null, evaluation);
            replay.close(); externalContext = context;
        }
        boolean needs = match.analysisEnabled() && context.result().equals("IN_PROGRESS") && frame.hint() == null && "null".equals(frame.evaluation());
        return prepared(state("APPLIED"), needs ? context : null);
    }

    String applyExternal(int revision, String key, String result) {
        if (frame == null || externalContext == null || revision != frame.revision() || !key.equals(externalContext.contextKey())) return state("STALE");
        var observed = ExternalEngineAnalysis.batch(ChessMatch.restore(frame.checkpoint()), frame.ply(), result);
        WebExternalAnalysis.requireCurrent(observed.provider());
        var hint = MatchHint.external(frame.position(), observed);
        frame = new Frame(frame.checkpoint(), frame.ply(), frame.revision(), frame.position(), hint, WebExternalAnalysis.evaluation(frame.position(), observed));
        replay.close();
        return state("APPLIED");
    }

    private static String prepared(String state, ExternalEngineAnalysis.Context context) {
        return "{\"state\":" + state + ",\"request\":" + (context == null ? "null" : context.json()) + "}";
    }

    String open(String checkpoint, int ply, int revision) {
        externalContext = null;
        if (frame != null && revision <= frame.revision()) {
            boolean sameFrame = revision == frame.revision() && ply == frame.ply() && checkpoint.equals(frame.checkpoint());
            return state(sameFrame ? "APPLIED" : "STALE");
        }
        var match = ChessMatch.restore(checkpoint);
        if (ply < 0 || ply > match.ply()) throw new IllegalArgumentException("Analysis position is outside the match");
        var position = match.positionAt(ply);
        MatchHint hint = null;
        String evaluation = "null";
        if (match.analysisEnabled()) {
            if (ply == match.ply() && match.result() != Result.IN_PROGRESS) {
                evaluation = WebBoardState.terminalEvaluation(match.result());
            } else {
                var cached = cache.find(checkpoint, ply).orElse(null);
                if (cached != null) position = cached.position();
                var analyzed = cached == null ? match.hintAt(ply) : cached.hint();
                var analyzedEvaluation = analyzed.evaluation(position);
                evaluation = WebBoardState.evaluation(analyzedEvaluation);
                if (analyzed.count() > 0 && analyzedEvaluation != null) {
                    hint = analyzed;
                    if (cached == null) cache.remember(checkpoint, ply, position, analyzed);
                }
            }
        } else {
            cache.clear();
        }
        replay.close();
        frame = new Frame(checkpoint, ply, revision, position, hint, evaluation);
        return state("APPLIED");
    }

    String action(int revision, String action, int index) {
        if (frame == null) return state("UNAVAILABLE");
        if (revision != frame.revision()) return state("STALE");
        boolean applied = switch (action) {
            case "hint" -> reveal();
            case "preview" -> replay.open(frame.position(), frame.hint());
            case "preview-close" -> replay.close();
            case "preview-branch" -> replay.select(index);
            case "preview-seek" -> replay.seek(index);
            case "preview-proof" -> replay.prove();
            default -> throw new IllegalArgumentException("Unknown match analysis action");
        };
        return state(applied ? "APPLIED" : "UNAVAILABLE");
    }

    private boolean reveal() {
        if (frame.hint() == null || replay.isOpen()) return false;
        frame.hint().revealNext();
        return true;
    }

    private String state(String outcome) {
        String fields = "{\"outcome\":" + quote(outcome) + ",\"analysis\":";
        if (frame == null) return fields + "null}";
        return fields + "{\"revision\":" + frame.revision() + ",\"ply\":" + frame.ply()
                + ",\"evaluation\":" + frame.evaluation() + ",\"hint\":" + hint(frame.hint()) + ",\"preview\":" + replay.state()
                + (externalContext == null ? "" : ",\"contextKey\":" + quote(externalContext.contextKey()) + ",\"pending\":" + (frame.hint() == null && "null".equals(frame.evaluation()))
                    + ",\"engine\":" + (frame.hint() == null ? "null" : frame.hint().external().map(batch -> batch.provider().json()).orElse("null"))) + "}}";
    }

    private static String hint(MatchHint hint) {
        return hint == null ? "null" : "{\"count\":" + hint.count() + ",\"number\":" + hint.number()
                + ",\"move\":" + move(hint.revealedMove()) + "}";
    }

    private record Frame(String checkpoint, int ply, int revision, ChessPosition position, MatchHint hint, String evaluation) { }
}
