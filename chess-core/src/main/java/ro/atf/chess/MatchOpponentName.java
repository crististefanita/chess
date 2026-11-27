package ro.atf.chess;

import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Shared opponent display-name choices for newly created and legacy matches. */
public final class MatchOpponentName {
    private static final List<String> NAMES = List.of(
            "Alex", "Mira", "Atlas", "Sofia", "Leo", "Nika", "Victor", "Emma",
            "David", "Iris", "Mateo", "Lena", "Noah", "Clara", "Radu", "Elena");

    private MatchOpponentName() { }

    public static String forLevel(MatchSetup.Level level) {
        return switch (level) {
            case BASIC -> "Alex";
            case ADVANCED -> "Mira";
            case HARD -> "Atlas";
        };
    }

    /** Selects one display identity when a new match is created. */
    public static String random(IntUnaryOperator draw) {
        Objects.requireNonNull(draw);
        int index = draw.applyAsInt(NAMES.size());
        if (index < 0 || index >= NAMES.size()) throw new IllegalArgumentException("Opponent name index is out of range");
        return NAMES.get(index);
    }

    public static List<String> choices() { return NAMES; }
}
