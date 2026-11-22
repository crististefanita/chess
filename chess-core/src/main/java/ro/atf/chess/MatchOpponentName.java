package ro.atf.chess;

/** Stable opponent identity shared by the native and browser match adapters. */
public final class MatchOpponentName {
    private MatchOpponentName() { }

    public static String forLevel(MatchSetup.Level level) {
        return switch (level) {
            case BASIC -> "Alex";
            case ADVANCED -> "Mira";
            case HARD -> "Atlas";
        };
    }
}
