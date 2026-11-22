package ro.atf.chess;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

/** Reuses only complete analysis for positions in the unchanged active match. */
public final class MatchAnalysisCache {
    private static final int MAX_POSITIONS = 64;
    private final LinkedHashMap<Integer, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private String checkpoint;

    public Optional<Entry> find(String matchCheckpoint, int ply) {
        select(matchCheckpoint, ply);
        var entry = entries.get(ply);
        if (entry != null) entry.hint().hide();
        return Optional.ofNullable(entry);
    }

    public void remember(String matchCheckpoint, int ply, ChessPosition position, MatchHint hint) {
        select(matchCheckpoint, ply);
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(hint, "hint");
        if (hint.count() == 0 || hint.proof().filter(CoachingProof.Work::complete).isEmpty()) return;
        hint.hide();
        entries.put(ply, new Entry(position, hint));
        while (entries.size() > MAX_POSITIONS) {
            var oldest = entries.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    public void clear() {
        checkpoint = null;
        entries.clear();
    }

    private void select(String matchCheckpoint, int ply) {
        Objects.requireNonNull(matchCheckpoint, "matchCheckpoint");
        if (ply < 0) throw new IllegalArgumentException("Negative analysis ply");
        if (!matchCheckpoint.equals(checkpoint)) {
            checkpoint = matchCheckpoint;
            entries.clear();
        }
    }

    /** The position identity is retained because MatchHint validates its source by identity. */
    public record Entry(ChessPosition position, MatchHint hint) { }
}
