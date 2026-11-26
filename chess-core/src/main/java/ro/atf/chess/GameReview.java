package ro.atf.chess;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.function.IntConsumer;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** Local analysis of the recorded line, with independent scores for both sides. */
public record GameReview(List<Turn> turns, Opening opening, int bookPlies, int initialWhitePoints,
                         Player white, Player black, ExternalEngineAnalysis.Review external) {
    public GameReview(List<Turn> turns, Opening opening, int bookPlies, int initialWhitePoints, Player white, Player black) {
        this(turns, opening, bookPlies, initialWhitePoints, white, black, null);
    }
    public enum Grade { BOOK, FORCED, BRILLIANT, GREAT, BEST, EXCELLENT, GOOD, INACCURACY, MISTAKE, MISS, BLUNDER }
    public record GradeCount(Grade grade, int human, int ai) { }

    public List<GradeCount> compareGrades(Side humanSide) {
        var human = humanSide == Side.WHITE ? white : black;
        var ai = humanSide == Side.WHITE ? black : white;
        var counts = new ArrayList<GradeCount>();
        for (var grade : List.of(Grade.BRILLIANT, Grade.GREAT, Grade.BEST, Grade.EXCELLENT, Grade.GOOD, Grade.BOOK,
                Grade.FORCED, Grade.INACCURACY, Grade.MISTAKE, Grade.MISS, Grade.BLUNDER)) {
            counts.add(new GradeCount(grade, human.grades().getOrDefault(grade, 0), ai.grades().getOrDefault(grade, 0)));
        }
        return List.copyOf(counts);
    }
    public record Rating(int elo, Integer low, int high) { }
    public record Opening(String eco, String name) { }
    public record Player(OptionalInt accuracy, Optional<Rating> rating, int ratedMoves, Map<Grade, Integer> grades) {
        public Player { grades = Map.copyOf(grades); }
    }
    public record Turn(int ply, Side side, Move played, Grade grade, int bestPoints, int playedPoints, int loss,
                       int whitePoints, int depth, List<Move> bestLine, List<Move> playedLine, int sacrificeCp,
                       CoachingProof.Work proof) {
        public Turn(int ply,Side side,Move played,Grade grade,int bestPoints,int playedPoints,int loss,int whitePoints,int depth,List<Move> bestLine,List<Move> playedLine,int sacrificeCp) {
            this(ply,side,played,grade,bestPoints,playedPoints,loss,whitePoints,depth,bestLine,playedLine,sacrificeCp,null);
        }
        public Turn {
            bestLine = List.copyOf(bestLine);
            playedLine = List.copyOf(playedLine);
            if(proof!=null&&!proof.complete())throw new IllegalArgumentException("Incomplete tactical proof cannot publish a graded review turn");
        }
    }

    public GameReview { turns = List.copyOf(turns); }

    /** Builds a review from matched external observations, without native search or invented proof. */
    public static GameReview external(ChessMatch match, ExternalEngineAnalysis.Review observations) {
        // Revalidate records supplied directly by a Java caller, as well as serialized browser observations.
        observations = ExternalEngineAnalysis.review(match, observations.json());
        var book = OpeningIndex.CURRENT.recognize(match);
        var white = new PlayerStats(); var black = new PlayerStats(); var turns = new ArrayList<Turn>();
        int initial = 500;
        for (var observed : observations.turns()) {
            int ply = observed.ply(); var board = match.positionAt(ply - 1); var move = match.moveAt(ply);
            int best = observed.free().best().expectedPoints(), actual = observed.played().best().expectedPoints();
            int loss = Math.max(0, best - actual); var side = board.turn();
            boolean bookMove = ply <= book.bookPlies() && loss < GameReviewPolicy.CURRENT.classification.bookLoss();
            // No native verified-depth, sacrifice or exhaustive-mate claim is inferred from an engine depth.
            Grade grade = classify(bookMove, board.legalMoves().size() == 1, move.equals(observed.free().best().root()),
                    0, best, actual, best, 0, turns.isEmpty() ? 0 : turns.get(turns.size() - 1).loss(), false);
            if (ply == 1) initial = side == Side.WHITE ? best : 1000 - best;
            turns.add(new Turn(ply, side, move, grade, best, actual, loss, side == Side.WHITE ? actual : 1000 - actual,
                    observed.free().depth(), observed.free().best().pv(), observed.played().best().pv(), 0, null));
            (side == Side.WHITE ? white : black).add(grade, loss);
        }
        if (observations.rated() && match.result() == ChessMatch.Result.IN_PROGRESS)
            throw new IllegalArgumentException("Finish the game before estimating its rating");
        boolean rated = observations.rated() && !(match.setup().level() == MatchSetup.Level.HARD && HardMaster.usesA());
        var policy = GameReviewPolicy.CURRENT;
        var opponent = policy.opponentRatings.get(match.setup().level());
        var human = match.setup().humanSide();
        var humanStats = human == Side.WHITE ? white : black;
        var humanRating = rated ? estimate(match.result(), human, humanStats.choices, opponent, policy.minimumRatedMoves) : Optional.<Rating>empty();
        var botRating = rated ? Optional.of(opponent) : Optional.<Rating>empty();
        return new GameReview(turns, book.opening(), book.bookPlies(), initial,
                white.finish(human == Side.WHITE ? humanRating : botRating),
                black.finish(human == Side.BLACK ? humanRating : botRating), observations);
    }

    public static GameReview analyze(ChessMatch match, IntConsumer progress) {
        if (match.result() == ChessMatch.Result.IN_PROGRESS) throw new IllegalArgumentException("Finish the game before reviewing it");
        return analyze(match, progress, true);
    }

    /** Reviews every recorded move without deriving a rating from a study or unfinished game. */
    public static GameReview analyzeLine(ChessMatch match, IntConsumer progress) {
        if (match.ply() == 0) throw new IllegalArgumentException("Play a move before reviewing the line");
        return analyze(match, progress, false);
    }

    private static GameReview analyze(ChessMatch match, IntConsumer progress, boolean rated) {
        return analyze(match, progress, rated, new ReviewJournal(match), true);
    }

    public static GameReview prepare(ChessMatch match, ReviewJournal journal, IntConsumer progress) {
        return analyze(match, progress, false, journal, true);
    }

    /** No engine search: fails explicitly if any played move still lacks complete evidence. */
    public static GameReview aggregate(ChessMatch match, ReviewJournal journal, boolean rated) {
        if (rated && match.result() == ChessMatch.Result.IN_PROGRESS) throw new IllegalArgumentException("An unfinished match is unrated");
        return analyze(match, ignored -> { }, rated, journal, false);
    }

    private static GameReview analyze(ChessMatch match, IntConsumer progress, boolean rated, ReviewJournal journal, boolean calculate) {
        journal.align(match);
        // The experimental A engine has no qualified historical opponent rating.
        rated = rated && !(match.setup().level() == MatchSetup.Level.HARD && HardMaster.usesA());
        var policy = GameReviewPolicy.CURRENT;
        var book = OpeningIndex.CURRENT.recognize(match);
        var turns = new ArrayList<Turn>(match.ply());
        var white = new PlayerStats();
        var black = new PlayerStats();
        int initial = 500;
        var before = match.continueFrom(0);
        for (int ply = 1; ply <= match.ply(); ply++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            var board = before.position();
            var played = match.moveAt(ply);
            var context = before;
            var sample = calculate ? journal.obtain(ply, () -> inspect(context, played)) : journal.require(ply);
            int best = sample.best();
            int actual = sample.actual();
            int loss = Math.max(0, best - actual);
            int alternative = sample.alternative();
            int sacrifice = sample.sacrifice();
            boolean bookMove = ply <= book.bookPlies() && loss < policy.classification.bookLoss();
            boolean forced = board.legalMoves().size() == 1;
            int previousLoss = turns.isEmpty() ? 0 : turns.get(turns.size() - 1).loss();
            Grade grade = classify(policy.classification, bookMove, forced, sample.bestMove(),
                    sample.depth(), best, actual, alternative, sacrifice, previousLoss, sample.onlyMate());
            var side = board.turn();
            if (ply == 1) initial = side == Side.WHITE ? best : 1000 - best;
            turns.add(new Turn(ply, side, played, grade, best, actual, loss, side == Side.WHITE ? actual : 1000 - actual,
                    sample.depth(), sample.bestLine(), sample.playedLine(), sacrifice, sample.proof()));
            (side == Side.WHITE ? white : black).add(grade, loss);
            before = before.play(played);
            progress.accept(ply);
        }
        var opponent = policy.opponentRatings.get(match.setup().level());
        var human = match.setup().humanSide();
        var humanStats = human == Side.WHITE ? white : black;
        var humanRating = rated ? estimate(match.result(), human, humanStats.choices, opponent, policy.minimumRatedMoves)
                : Optional.<Rating>empty();
        var botRating = rated ? Optional.of(opponent) : Optional.<Rating>empty();
        return new GameReview(turns, book.opening(), book.bookPlies(), initial,
                white.finish(human == Side.WHITE ? humanRating : botRating),
                black.finish(human == Side.BLACK ? humanRating : botRating));
    }

    private static ReviewJournal.Sample inspect(ChessMatch before, Move played) {
        var policy = GameReviewPolicy.CURRENT;
        var board = before.position();
        var comparison = TacticalAnalysis.review(board, played, before.automaticDrawMoves(), policy.search);
        return new ReviewJournal.Sample(points(comparison.best().score(), policy.scoreScale), points(comparison.played().score(), policy.scoreScale),
                points(comparison.alternativeScore(), policy.scoreScale), comparison.depth(),
                Math.max(0, TacticalAnalysis.offeredMaterial(board, comparison.played())), comparison.ordinaryNodes(),
                comparison.played().score() >= comparison.best().score(), comparison.onlyProvedMate(),
                comparison.best().line(), comparison.played().line(), comparison.proof());
    }

    static int points(int score, int scale) {
        return (int) Math.round(1000 / (1 + Math.exp(-score / (double) scale)));
    }

    static Grade classify(boolean book, boolean forced, boolean bestMove, int depth, int best, int played,
                          int alternative, int sacrifice, int previousLoss, boolean onlyProvedMate) {
        return classify(GameReviewPolicy.CURRENT.classification, book, forced, bestMove, depth, best, played,
                alternative, sacrifice, previousLoss, onlyProvedMate);
    }

    static Grade classify(GameReviewPolicy.Classification policy, boolean book, boolean forced, boolean bestMove,
                          int depth, int best, int played, int alternative, int sacrifice, int previousLoss, boolean onlyProvedMate) {
        if (book) return Grade.BOOK;
        if (forced) return Grade.FORCED;
        int loss = Math.max(0, best - played);
        boolean verified = depth >= policy.verifiedDepth() && bestMove;
        boolean distinct = best - alternative >= policy.distinctGap();
        if (verified && (distinct || onlyProvedMate) && played >= policy.brilliantPlayed()
                && sacrifice >= policy.brilliantSacrifice()) return Grade.BRILLIANT;
        if (verified && distinct && (best >= policy.winningBest() && alternative < policy.winningAlternative()
                || best >= policy.recoveryBest() && alternative < policy.recoveryAlternative())) return Grade.GREAT;
        if (bestMove) return Grade.BEST;
        if (previousLoss >= policy.missPreviousLoss() && best >= policy.missBest() && played <= policy.missPlayed()) return Grade.MISS;
        if (loss >= policy.blunder()) return Grade.BLUNDER;
        if (loss >= policy.mistake()) return Grade.MISTAKE;
        if (loss >= policy.inaccuracy()) return Grade.INACCURACY;
        return loss >= policy.good() ? Grade.GOOD : Grade.EXCELLENT;
    }

    static Optional<Rating> estimate(ChessMatch.Result result, Side human, int moves, Rating opponent, int minimum) {
        if (moves < minimum) return Optional.empty();
        double whiteScore = MatchEvaluation.terminalWhiteShare(result);
        double score = human == Side.WHITE ? whiteScore : 1 - whiteScore;
        // One observed game updates a uniform prior on expected score against the reference opponent.
        int center = opponent.elo() + eloDifference((1 + score) / 3);
        Integer low = opponent.low() == null ? null : opponent.low() + eloDifference(scoreQuantile(score, .05));
        int high = opponent.high() + eloDifference(scoreQuantile(score, .95));
        return Optional.of(new Rating(center, low, high));
    }

    private static int eloDifference(double score) {
        return (int) Math.round(400 * Math.log10(score / (1 - score)));
    }

    private static double scoreQuantile(double score, double quantile) {
        if (score == 1) return Math.sqrt(quantile);
        if (score == 0) return 1 - Math.sqrt(1 - quantile);
        // The half-point posterior is symmetric. Its integral can be inverted by bisection.
        double low = 0;
        double high = 1;
        for (int step = 0; step < 32; step++) {
            double mid = (low + high) / 2;
            double x = 2 * mid - 1;
            double cumulative = .5 + (Math.asin(x) + x * Math.sqrt(1 - x * x)) / Math.PI;
            if (cumulative < quantile) low = mid;
            else high = mid;
        }
        return (low + high) / 2;
    }

    private static final class PlayerStats {
        private final Map<Grade, Integer> grades = new EnumMap<>(Grade.class);
        private int choices;
        private long totalLoss;

        void add(Grade grade, int loss) {
            grades.merge(grade, 1, Integer::sum);
            if (grade == Grade.BOOK || grade == Grade.FORCED) return;
            choices++;
            totalLoss += loss;
        }

        Player finish(Optional<Rating> rating) {
            var accuracy = choices == 0 ? OptionalInt.empty() : OptionalInt.of((int) Math.round(100 - totalLoss / (10.0 * choices)));
            return new Player(accuracy, rating, choices, grades);
        }
    }
}
