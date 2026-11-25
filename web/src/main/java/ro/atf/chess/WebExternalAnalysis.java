package ro.atf.chess;

/** Current Web integration identity; archived observations retain their original provider. */
final class WebExternalAnalysis {
    private WebExternalAnalysis() { }

    static void requireCurrent(ExternalEngineAnalysis.Provider provider) {
        if (!provider.providerID().equals("stockfish-19.0.1-full-single") || !provider.version().equals("19.0.1")
                || !provider.buildSha256().equals("991bede2f253ebb08ff01fcc48270f4b9c8e66b12d5bd5f8d6f52eb3e3b3a740")
                || !provider.sourceCommit().equals("0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6")) {
            throw new IllegalArgumentException("The result is not from the configured Web engine");
        }
    }

    static String evaluation(ChessPosition position, ExternalEngineAnalysis.Batch batch) {
        var value = batch.best().evaluation(position.turn());
        return "{\"ending\":" + WebBoardState.quote(value.ending().name()) + ",\"whiteShare\":" + whiteShare(position, batch)
                + ",\"centipawns\":" + value.whiteCentipawns() + ",\"mate\":" + value.whiteMateMoves()
                + ",\"engine\":" + batch.provider().json() + "}";
    }

    static double whiteShare(ChessPosition position, ExternalEngineAnalysis.Batch batch) {
        int points = batch.best().expectedPoints();
        return (position.turn() == ChessPosition.Side.WHITE ? points : 1000 - points) / 1000.0;
    }

    static String provider(GameReview review) {
        return review.external() == null ? "{\"providerID\":\"native\",\"label\":\"Chess Lab\"}" : review.external().provider().json();
    }
}
