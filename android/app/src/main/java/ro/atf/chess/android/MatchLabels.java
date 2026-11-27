package ro.atf.chess.android;

import android.content.Context;
import ro.atf.chess.MatchSetup;

final class MatchLabels {
    private MatchLabels() { }

    static String botName(Context context, String opponentName, MatchSetup.Level level) {
        int label = switch (level) {
            case BASIC -> R.string.match_basic;
            case ADVANCED -> R.string.match_advanced;
            case HARD -> R.string.match_hard;
        };
        return context.getString(R.string.match_bot_label, opponentName, context.getString(label));
    }
}
