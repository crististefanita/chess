package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collection;
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
        return random(draw, List.of());
    }

    /** Selects an identity not already assigned to a retained AI match. */
    public static String random(IntUnaryOperator draw, Collection<String> usedNames) {
        Objects.requireNonNull(draw);
        Objects.requireNonNull(usedNames);
        var available = new ArrayList<String>();
        for (String name : NAMES) if (!usedNames.contains(name)) available.add(name);
        if (available.isEmpty()) throw new IllegalStateException("No unused opponent names remain");
        int index = draw.applyAsInt(available.size());
        if (index < 0 || index >= available.size()) throw new IllegalArgumentException("Opponent name index is out of range");
        return available.get(index);
    }

    public static List<String> choices() { return NAMES; }
}
