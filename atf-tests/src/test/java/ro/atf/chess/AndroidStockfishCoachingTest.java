package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static ro.atf.chess.ChessPosition.Move.fromUci;

/** Portable execution of APK display rules plus explicit adapter-boundary governance. No emulator or engine. */
@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class AndroidStockfishCoachingTest {
    @TempDir Path compiled;

    @Test void allOpeningPrefixesReachTheProviderWithTheirCompleteHistory(Checks c) throws Exception {
        try (var loader = presentation(c)) {
            var type = loader.loadClass("ro.atf.chess.android.CoachingPresentation");
            var opening = type.getMethod("opening", OpeningCatalog.Opening.class, int.class);
            int prefixes = 0;
            for (var lesson : OpeningCatalog.load()) {
                var expected = ChessMatch.start(true);
                for (int ply = 0; ply <= lesson.steps().size(); ply++) {
                    var actual = (ChessMatch) opening.invoke(null, lesson, ply);
                    c.same(lesson.id() + " prefix " + ply + " retains the legal board", lesson.positionAt(ply).searchKey(), actual.position().searchKey());
                    c.same(lesson.id() + " prefix " + ply + " retains full history and clocks",
                            ExternalEngineAnalysis.context(expected, ply), ExternalEngineAnalysis.context(actual, ply));
                    if (ply < lesson.steps().size()) expected = expected.play(lesson.steps().get(ply).move());
                    prefixes++;
                }
            }
            c.same("The actual shipped catalog supplies nonempty runnable prefixes", true, prefixes > 5);
            var sample = OpeningCatalog.load().get(0);
            c.rejects("An invalid lesson cursor cannot silently analyze another frame", InvocationTargetException.class,
                    () -> opening.invoke(null, sample, -1));
        }
    }

    @Test void barUsesTheSharedAdvantageScaleAndPreservesObservedWdl(Checks c) throws Exception {
        try (var loader = presentation(c)) {
            var type = loader.loadClass("ro.atf.chess.android.CoachingPresentation");
            var share = type.getMethod("whiteShare", MatchHint.class, ChessPosition.class);
            var white = ChessMatch.start(true);
            var black = white.play(fromUci("e2e4"));
            for (var match : List.of(white, black)) {
                String root = match.position().turn() == ChessPosition.Side.WHITE ? "e2e4" : "e7e5";
                var line = new ExternalEngineAnalysis.Line(1, 12, 100, null, List.of(1000, 0, 0), List.of(fromUci(root)));
                var batch = new ExternalEngineAnalysis.Batch(ExternalEngineAnalysis.context(match, match.ply()),
                        ExternalEngineAnalysisTest.PROVIDER, 12, List.of(line));
                var hint = MatchHint.external(match.position(), ExternalEngineAnalysis.batch(match, match.ply(), batch.json()));
                c.same("One pawn stays moderate even when observed WDL saturates: " + match.position().turn(),
                        match.position().turn() == ChessPosition.Side.WHITE ? .6 : .4, share.invoke(null, hint, match.position()));
                c.same("The raw WDL observation is unchanged", List.of(1000, 0, 0), hint.external().orElseThrow().best().wdl());
                c.same("The recorded expected points retain their original meaning", 1000, hint.external().orElseThrow().best().expectedPoints());
            }
            c.rejects("Missing provider evidence cannot produce an external advantage", InvocationTargetException.class,
                    () -> share.invoke(null, new MatchHint(List.of()), white.position()));
        }
    }

    @Test void equalPlacementWithDifferentHistoryRemainsADifferentCoachingRequest(Checks c) throws Exception {
        try (var loader = presentation(c)) {
            var source = loader.loadClass("ro.atf.chess.android.CoachingPresentation").getMethod("source", ChessMatch.class, int.class);
            var initial = ChessMatch.start(true);
            var repetition = ChessMatch.restore("2|analysis|g1f3 g8f6 f3g1 f6g8");
            c.same("The board placement really is equal", initial.position().searchKey(), repetition.position().searchKey());
            c.same("History and reversible clocks remain part of request identity", false,
                    source.invoke(null, initial, 0).equals(source.invoke(null, repetition, 4)));
            var enPassant = ChessMatch.restore("2|analysis|e2e4 a7a6 e4e5 d7d5");
            c.same("Immediate en passant is retained by the same provider boundary", true,
                    ExternalEngineAnalysis.context(enPassant, 4).legalMoves().contains("e5d6"));
        }
    }

    @Test void externalPreviewReplaysEveryOfferedLineWithoutNativeLeafScores(Checks c) throws Exception {
        try (var loader = presentation(c)) {
            var presentation = loader.loadClass("ro.atf.chess.android.CoachingPresentation");
            var preview = presentation.getMethod("preview", ChessPosition.class, TacticalAnalysis.Variation.class);
            var match = ChessMatch.start(true);
            var observed = new java.util.ArrayList<ExternalEngineAnalysis.Line>();
            int rank = 0;
            for (String move : List.of("e2e4", "d2d4", "g1f3", "c2c4", "b1c3"))
                observed.add(new ExternalEngineAnalysis.Line(++rank, 12, 25, null, List.of(300, 500, 200), List.of(fromUci(move))));
            var batch = new ExternalEngineAnalysis.Batch(ExternalEngineAnalysis.context(match, 0), ExternalEngineAnalysisTest.PROVIDER, 12, observed);
            var hint = MatchHint.external(match.position(), ExternalEngineAnalysis.batch(match, 0, batch.json()));
            c.same("Only the three strongest comparable recommendations are offered", 3, hint.count());
            for (var line : hint.lines()) {
                var replay = (HintPreview) preview.invoke(null, match.position(), line);
                c.same(line.move() + " retains no fabricated native estimate", true, replay.estimate().isEmpty());
                c.same(line.move() + " starts at the original cursor", 0, replay.step());
                replay.next();
                c.same(line.move() + " ends on exactly the observed move", line.move(), replay.lastMove());
                c.same(line.move() + " has no appended unscored defence", false, replay.addedDefense());
                replay.previous();
                c.same(line.move() + " returns without changing the saved match", match.position(), replay.position());
            }
            c.same("The five-branch preview does not modify the checkpoint", ChessMatch.start(true).checkpoint(), match.checkpoint());
            var display = ChessPosition.initial();
            c.same("The display board is an independent object", false, display == match.position());
            var rebound = (MatchHint) presentation.getMethod("bind", ChessPosition.class, MatchHint.class).invoke(null, display, hint);
            rebound.revealNext();
            var move = rebound.revealedMove();
            c.same("The engine recommendation applies to the actual displayed board", move, rebound.moveForTap(display, -1, move.to()));
            c.rejects("Another actual position cannot borrow the hint", InvocationTargetException.class,
                    () -> presentation.getMethod("bind", ChessPosition.class, MatchHint.class).invoke(null,
                            display.play(fromUci("a2a3")), hint));
        }
    }

    @Test void coachingRoutesCannotFallBackToTheNativeOpponentKernel(Checks c) throws Exception {
        var folder = ProjectRoot.locate().resolve("android/app/src/main/java/ro/atf/chess/android");
        String hint = Files.readString(folder.resolve("MatchAnalysisView.java"));
        String review = Files.readString(folder.resolve("GameReviewDialog.java"));
        String game = Files.readString(folder.resolve("ChessActivity.java"));
        String opening = Files.readString(folder.resolve("OpeningActivity.java"));
        c.same("Every match/manual/opening supplier reaches Stockfish with a full match", true,
                hint.contains("owner.hint(match, ply)") && hint.contains("show(line.line(), line.ply()")
                        && hint.contains("CoachingPresentation.opening(opening, ply)"));
        for (String call : List.of("match.hintAt(", "MatchHint.analyze(", "OpeningReference.analyze(", "line::hint", "void show(ChessPosition"))
            c.same("No board-only or native fallback in hints: " + call, false, hint.contains(call));
        c.same("The final review requires original external observations", true,
                review.contains("owner.review(match,") && review.contains("if (result.external() == null) throw"));
        for (String call : List.of("GameReview.analyze(", "GameReview.analyzeLine(", "GameReview.aggregate(", "GameReview.prepare("))
            c.same("No native review fallback: " + call, false, review.contains(call));
        c.same("The incremental collector also uses external coaching", true,
                game.contains("request -> prepareStockfishReview(request.match()") && !game.contains(".prepareReview("));
        c.same("Hard remains the opponent, independent of coaching", true, game.contains("pending.match().chooseBotMove()"));
        c.same("Cancelling a hint closes only its active owner", true,
                hint.contains("pending.cancel(true)") && hint.contains("closeCoaching();") && hint.contains("request != currentRequest"));
        c.same("Disabling opening analysis suspends the owner before hiding its panel", true,
                opening.substring(opening.indexOf("aiEnabled = !aiEnabled;"),
                        opening.indexOf("render();", opening.indexOf("aiEnabled = !aiEnabled;"))).contains("analysis.suspend();"));
        c.same("Legacy review provenance and explicit reanalysis remain visible", true,
                review.contains("game_review_legacy_engine") && review.contains("game_review_stockfish_reanalyze"));
    }

    private URLClassLoader presentation(Checks c) throws Exception {
        var source = ProjectRoot.locate().resolve("android/app/src/main/java/ro/atf/chess/android/CoachingPresentation.java");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        int status = ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-proc:none",
                "-classpath", classpath, "-d", compiled.toString(), source.toString());
        c.same("The real APK presentation component compiles without Android classes", 0, status);
        return new URLClassLoader(new java.net.URL[] {compiled.toUri().toURL()}, getClass().getClassLoader());
    }
}
