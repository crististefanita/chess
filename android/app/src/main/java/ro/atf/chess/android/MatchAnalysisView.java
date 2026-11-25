package ro.atf.chess.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.TextView;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.IntSupplier;
import ro.atf.chess.AnalysisLine;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.MatchEvaluation;
import ro.atf.chess.MatchAnalysisCache;
import ro.atf.chess.MatchHint;
import ro.atf.chess.OpeningCatalog;
import ro.atf.chess.OpeningReference;
import ro.atf.chess.QuestHint;
import ro.atf.chess.TacticalAnalysis;
import ro.atf.chess.ReplayCheckpoint;
import java.util.List;

/** Displays analysis for the current frame, independently of the computer's move. */
final class MatchAnalysisView extends TextView {
    private final Function<Move, String> notation;
    private final ImageButton bulb;
    private final EvaluationBar evaluationBar;
    private final IntSupplier sourceBoardSide;
    private final Runnable renderBoard;
    private final Runnable recommendationReady;
    private final MatchAnalysisCache analysisCache = new MatchAnalysisCache();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ChessPosition shownPosition;
    private double shownTerminal = Double.NaN;
    private MatchHint hint;
    private MatchEvaluation shownEvaluation;
    private HintPreviewDialog.Session preview;
    private String shownSource;
    private ReplayCheckpoint pendingRestoration;
    private Future<?> pending;
    private int request;

    MatchAnalysisView(Context context, Function<Move, String> notation, Runnable renderBoard, IntSupplier sourceBoardSide) {
        this(context, notation, renderBoard, sourceBoardSide, null);
    }

    /** A lesson presents its first recommendation together with the measured score. */
    MatchAnalysisView(Context context, Function<Move, String> notation, Runnable renderBoard, IntSupplier sourceBoardSide,
                      Runnable recommendationReady) {
        super(context);
        this.notation = notation;
        this.sourceBoardSide = sourceBoardSide;
        this.renderBoard = renderBoard;
        this.recommendationReady = recommendationReady;
        evaluationBar = new EvaluationBar(context);
        setId(R.id.match_analysis);
        setGravity(Gravity.CENTER);
        setTextSize(13);
        setTextColor(0xffc0bfb4);
        BoardCaption.reserve(this, 2);
        setText(R.string.match_analysis_pending);
        bulb = (ImageButton) LayoutInflater.from(context).inflate(R.layout.quest_guidance, null);
        bulb.setId(R.id.match_hint);
        bulb.setOnClickListener(view -> {
            if (hint == null) return;
            hint.revealNext();
            display();
            renderBoard.run();
            announceForAccessibility(getContentDescription());
        });
        updateBulb();
    }

    ImageButton bulb() { return bulb; }
    EvaluationBar evaluationBar() { return evaluationBar; }

    Move revealedMove() {
        return hint == null ? null : hint.revealedMove();
    }

    Move moveForTap(ChessPosition position, int selected, int square) {
        return position == shownPosition && hint != null ? hint.moveForTap(position, selected, square) : null;
    }

    QuestHint.Cue cue() {
        var move = revealedMove();
        return move == null ? new QuestHint.Cue(-1, -1) : QuestHint.SOLUTION.cue(move);
    }

    void show(ChessMatch match, int ply) {
        double terminal = match.result() != ChessMatch.Result.IN_PROGRESS && ply == match.ply()
                ? MatchEvaluation.terminalWhiteShare(match.result()) : Double.NaN;
        String checkpoint = match.checkpoint();
        show(match.positionAt(ply), () -> match.hintAt(ply), terminal,
                "MATCH|" + checkpoint + "|" + ply, checkpoint, ply);
    }

    void show(ChessPosition position) {
        String board = java.util.stream.IntStream.range(0, 64).mapToObj(square -> String.valueOf(position.pieceAt(square)))
                .collect(java.util.stream.Collectors.joining(","));
        String legal = position.legalMoves().stream().map(Move::uci).sorted().collect(java.util.stream.Collectors.joining(" "));
        show(position, () -> MatchHint.analyze(position), Double.NaN, "POSITION|" + position.turn() + "|" + board + "|" + legal);
    }

    void showOpening(OpeningCatalog.Opening opening, int ply) {
        show(opening.positionAt(ply), () -> OpeningReference.analyze(opening, ply), Double.NaN,
                "OPENING|" + opening.id() + "|" + ply);
    }

    void show(AnalysisLine line) {
        double terminal = line.result() == ChessMatch.Result.IN_PROGRESS
                ? Double.NaN : MatchEvaluation.terminalWhiteShare(line.result());
        show(line.position(), line::hint, terminal, "STUDY|" + line.line().checkpoint() + "|" + line.ply());
    }

    private void show(ChessPosition position, Supplier<MatchHint> search, double terminal, String source) {
        show(position, search, terminal, source, null, -1);
    }

    private void show(ChessPosition position, Supplier<MatchHint> search, double terminal, String source,
                      String matchCheckpoint, int ply) {
        if (position == shownPosition && Double.compare(terminal, shownTerminal) == 0 && source.equals(shownSource)) return;
        var restoring = pendingRestoration;
        var cached = matchCheckpoint == null ? null : analysisCache.find(matchCheckpoint, ply).orElse(null);
        cancel();
        pendingRestoration = restoring != null && restoring.source().equals(source) ? restoring : null;
        shownSource = source;
        shownPosition = cached == null ? position : cached.position();
        shownTerminal = terminal;
        if (cached != null) {
            hint = cached.hint();
            shownEvaluation = hint.evaluation(shownPosition);
            if (shownEvaluation != null) {
                displayReady(request);
                return;
            }
            hint = null;
        }
        setText(R.string.match_analysis_pending);
        setContentDescription(null);
        int currentRequest = request;
        pending = worker.submit(() -> {
            var analyzed = search.get();
            var evaluation = analyzed.evaluation(position);
            post(() -> {
                if (request != currentRequest) return;
                pending = null;
                if (matchCheckpoint != null && evaluation != null)
                    analysisCache.remember(matchCheckpoint, ply, position, analyzed);
                if (evaluation == null) {
                    boolean incomplete=analyzed.proof().filter(p->!p.complete()).isPresent();
                    setText(incomplete?R.string.match_analysis_incomplete:R.string.match_analysis_ended);
                    evaluationBar.setWhiteShare(terminal);
                    return;
                }
                hint = analyzed;
                shownEvaluation = evaluation;
                displayReady(currentRequest);
            });
        });
    }

    private void displayReady(int currentRequest) {
        if (recommendationReady != null && hint.number() == 0) hint.revealNext();
        display();
        restorePreview(currentRequest);
        if (recommendationReady != null) recommendationReady.run();
    }

    void hideSuggestion() {
        if (revealedMove() == null) return;
        hint.hide();
        display();
    }

    void cancel() {
        if (preview != null) preview.dialog().dismiss();
        preview = null;
        pendingRestoration = null;
        shownSource = null;
        request++;
        shownPosition = null;
        shownTerminal = Double.NaN;
        hint = null;
        shownEvaluation = null;
        setOnClickListener(null);
        setClickable(false);
        evaluationBar.setWhiteShare(Double.NaN);
        updateBulb();
        if (pending != null) pending.cancel(true);
        pending = null;
    }

    String savedState() {
        if (preview != null && preview.dialog().isShowing()) return preview.checkpoint().get().encode();
        if (pendingRestoration != null) return pendingRestoration.encode();
        if (hint != null && hint.revealedMove() != null) {
            String move = hint.revealedMove().uci();
            return new ReplayCheckpoint(shownSource, ReplayCheckpoint.Kind.INLINE, move, List.of(move), 0, List.of(0), false, hint.number()).encode();
        }
        return null;
    }

    void restoreState(String state) { pendingRestoration = ReplayCheckpoint.decode(state).orElse(null); }

    void suspend() {
        String state = savedState();
        cancel();
        restoreState(state);
    }

    private void restorePreview(int expectedRequest) {
        var restored = pendingRestoration;
        if (restored == null || !restored.source().equals(shownSource)) return;
        for (int attempt = 0; attempt < hint.count(); attempt++) {
            hint.revealNext();
            if (hint.number() == restored.offer() && restored.suggestion().equals(hint.revealedMove().uci())) break;
        }
        if (hint.revealedMove() == null || hint.number() != restored.offer() || !restored.suggestion().equals(hint.revealedMove().uci())) {
            pendingRestoration = null;
            hint.hide();
            display();
            return;
        }
        display();
        renderBoard.run();
        post(() -> {
            if (request != expectedRequest || !restored.source().equals(shownSource)) return;
            if (restored.kind() == ReplayCheckpoint.Kind.CALCULATED) openPreview(restored);
            else if (restored.kind() == ReplayCheckpoint.Kind.THEORY) openTheory(restored);
            pendingRestoration = null;
        });
    }

    private void updateBulb() {
        boolean revealed = revealedMove() != null;
        bulb.setEnabled(hint != null);
        bulb.setSelected(revealed);
        bulb.setImageTintList(ColorStateList.valueOf(revealed ? 0xff57e0b4 : hint != null ? 0xffc0bfb4 : 0xff77766d));
        int label = !revealed ? R.string.match_hint_show : hint.count() > 1 ? R.string.match_hint_next : R.string.match_hint_shown;
        bulb.setContentDescription(getContext().getString(label));
    }

    private void display() {
        var context = getContext();
        var revealed = hint.revealed();
        var theory = hint.revealedTheory();
        var evaluation = shownEvaluation;
        evaluationBar.setWhiteShare(evaluation.whiteShare());
        String score = switch (evaluation.ending()) {
            case CHECKMATE -> context.getString(R.string.match_analysis_mate,
                    leadingColor(evaluation.whiteMateMoves()), Math.abs(evaluation.whiteMateMoves()));
            case STALEMATE -> context.getString(R.string.match_draw_stalemate);
            case HORIZON -> {
                String value = evaluation.pawnLabel(getResources().getConfiguration().getLocales().get(0));
                yield evaluation.whiteCentipawns() == 0 ? context.getString(R.string.match_analysis_equal, value)
                        : context.getString(R.string.match_analysis_advantage, value, leadingColor(evaluation.whiteCentipawns()));
            }
        };
        String spokenScore = evaluation.ending() == TacticalAnalysis.Ending.HORIZON
                ? context.getString(R.string.match_analysis_pawn_units, score) : score;
        var reference = hint.reference().orElse(null);
        if (reference != null) {
            var delta = new MatchEvaluation(TacticalAnalysis.Ending.HORIZON, reference.deltaWhiteCentipawns(), 0);
            var locale = getResources().getConfiguration().getLocales().get(0);
            score = context.getString(R.string.opening_reference_score, evaluation.pawnLabel(locale), delta.pawnLabel(locale));
            spokenScore = score + ". " + reference.engine() + ", " + reference.depth();
        }
        if (theory != null) {
            displayTheory(score, spokenScore, theory);
        } else if (revealed == null) {
            setText(score);
            setContentDescription(spokenScore);
            setOnClickListener(null);
            setClickable(false);
        } else {
            var spoken = new StringJoiner(". ");
            for (Move move : revealed.line()) spoken.add(notation.apply(move));
            String firstMove = ChessActivity.compactNotation(revealed.move());
            String choice = hint.count() > 1
                    ? context.getString(R.string.match_analysis_variant, hint.number(), hint.count(), firstMove) : firstMove;
            String action = context.getString(R.string.hint_preview_action, choice);
            String full = context.getString(R.string.match_analysis_variant_spoken, hint.number(), hint.count(), spoken.toString());
            suggestion(score, action, spokenScore + ". " + full + ". " + context.getString(R.string.hint_preview_open), this::openPreview);
        }
        updateBulb();
    }

    private void displayTheory(String score, String spokenScore, MatchHint.Theory theory) {
        var context = getContext();
        var locale = getResources().getConfiguration().getLocales().get(0);
        String firstMove = ChessActivity.compactNotation(theory.move());
        String family = theory.family().in(locale);
        String title = HintPreviewDialog.theoryTitle(context, theory);
        String action = hint.count() == 1 ? context.getString(R.string.hint_theory_caption, firstMove, family)
                : context.getString(R.string.hint_theory_numbered, hint.number(), hint.count(), firstMove, family);
        String detail = theory.idea() == null ? context.getString(R.string.hint_theory_index_detail)
                : theory.idea().in(locale);
        String move = notation.apply(theory.move());
        String spoken = context.getString(R.string.hint_theory_accessible, move, title, detail);
        suggestion(score, action, spokenScore + ". " + action + ". " + spoken, this::openTheory);
    }

    private void suggestion(String score, String action, String description, Runnable open) {
        var caption = new SpannableString(score + "\n" + action);
        caption.setSpan(new ForegroundColorSpan(0xff57e0b4), score.length() + 1, caption.length(), 0);
        setText(caption);
        setContentDescription(description);
        setOnClickListener(view -> open.run());
    }

    private void openTheory() {
        openTheory(null);
    }

    private void openTheory(ReplayCheckpoint restored) {
        if (hint == null || preview != null) return;
        hint.theoryPlayback(shownPosition).ifPresent(study ->
                rememberPreview(HintPreviewDialog.showTheory(getContext(), shownPosition, study, evaluationBar.blackBelow(), notation,
                        sourceBoardSide.getAsInt(), shownSource, hint.revealedMove().uci(), hint.number(), restored)));
    }

    private void openPreview() {
        openPreview(null);
    }

    private void openPreview(ReplayCheckpoint restored) {
        var position = shownPosition;
        var current = hint;
        if (current == null || preview != null) return;
        current.playback(position).ifPresent(playback ->
                rememberPreview(HintPreviewDialog.show(getContext(), position, playback, evaluationBar.blackBelow(), notation,
                        sourceBoardSide.getAsInt(), shownSource, hint.revealedMove().uci(), hint.number(), restored)));
    }

    private void rememberPreview(HintPreviewDialog.Session screen) {
        if (screen == null) return;
        preview = screen;
        screen.dialog().setOnDismissListener(dialog -> { if (preview == screen) preview = null; });
    }

    private String leadingColor(int advantage) { return getContext().getString(advantage > 0 ? R.string.white : R.string.black); }

    void close() {
        cancel();
        analysisCache.clear();
        worker.shutdown();
    }
}
