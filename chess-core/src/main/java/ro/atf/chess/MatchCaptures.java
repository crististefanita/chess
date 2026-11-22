package ro.atf.chess;

import ro.atf.chess.ChessPosition.Side;

/** Stable, orientation-aware capture rows for the currently displayed match frame. */
public record MatchCaptures(Player top, Player bottom) {
    /** The player owns this row; captured piece icons belong to the opposing side. */
    public record Player(Side side, MatchMaterial.Seat material) { }

    public static MatchCaptures at(ChessMatch match, int ply, boolean blackBelow) {
        if (ply < 0 || ply > match.ply()) throw new IllegalArgumentException("Capture frame is outside the match history");
        Side bottomSide = blackBelow ? Side.BLACK : Side.WHITE;
        var material = match.materialAt(ply);
        return new MatchCaptures(new Player(bottomSide.other(), material.forSide(bottomSide.other())),
                new Player(bottomSide, material.forSide(bottomSide)));
    }
}