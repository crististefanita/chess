package ro.atf.chess;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;
import ro.atf.chess.ChessPosition.Side;

/** Settings resolved once before a match starts and retained in its checkpoint. */
public record MatchSetup(Side humanSide, Level level, boolean analysisEnabled, String opponentName) {
    public MatchSetup {
        Objects.requireNonNull(humanSide);
        Objects.requireNonNull(level);
        Objects.requireNonNull(opponentName);
        if (!opponentName.matches("[A-Za-z][A-Za-z'-]{0,23}")) {
            throw new IllegalArgumentException("Invalid opponent name");
        }
    }

    public MatchSetup(Side humanSide, Level level, boolean analysisEnabled) {
        this(humanSide, level, analysisEnabled, MatchOpponentName.forLevel(level));
    }

    public static MatchSetup randomized(Side humanSide, Level level, boolean analysisEnabled, IntUnaryOperator draw) {
        return new MatchSetup(humanSide, level, analysisEnabled, MatchOpponentName.random(draw));
    }

    public MatchSetup withAnalysis(boolean enabled) {
        return new MatchSetup(humanSide, level, enabled, opponentName);
    }

    public enum ColorChoice {
        WHITE, BLACK, RANDOM;

        public Side resolve(BooleanSupplier randomBlack) {
            return switch (this) {
                case WHITE -> Side.WHITE;
                case BLACK -> Side.BLACK;
                case RANDOM -> randomBlack.getAsBoolean() ? Side.BLACK : Side.WHITE;
            };
        }
    }

    public enum Level {
        BASIC, ADVANCED, HARD
    }
}
