package ro.atf.chess.android;

import ro.atf.chess.ChessMatch;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ExternalEngineAnalysis;
import ro.atf.chess.GameReview;
import ro.atf.chess.HintPreview;
import ro.atf.chess.MatchHint;
import ro.atf.chess.OpeningCatalog;

/** Engine-neutral display rules; deliberately portable for JVM component checks. */
public final class CoachingPresentation {
    private CoachingPresentation() { }

    /** A board alone cannot preserve repetition, castling, en passant or the move clocks. */
    public static ChessMatch opening(OpeningCatalog.Opening opening, int ply) {
        if (opening == null || ply < 0 || ply > opening.steps().size())
            throw new IllegalArgumentException("Invalid opening prefix");
        var match = ChessMatch.start(true);
        for (int index = 0; index < ply; index++) match = match.play(opening.steps().get(index).move());
        return match;
    }

    public static String source(ChessMatch match, int ply) {
        return ExternalEngineAnalysis.context(match, ply).contextKey();
    }

    /** Display the shared gradual advantage scale; preserve observed WDL for review and evidence. */
    public static double whiteShare(MatchHint hint, ChessPosition position) {
        var observed = hint.external().orElseThrow(() -> new IllegalArgumentException("An external observation is required"));
        return observed.best().evaluation(position.turn()).whiteShare();
    }

    public static boolean legacy(GameReview review) { return review != null && review.external() == null; }

    public static MatchHint bind(ChessPosition displayed, MatchHint observed) {
        return MatchHint.external(displayed, observed.external().orElseThrow(() -> new IllegalArgumentException("Missing external observation")));
    }

    /** Replay only the observed PV. No native static leaf score or extra native search is introduced. */
    public static HintPreview preview(ChessPosition root, ro.atf.chess.TacticalAnalysis.Variation line) {
        return HintPreview.open(root, line).orElseThrow(() -> new IllegalArgumentException("Invalid external principal variation"));
    }
}
