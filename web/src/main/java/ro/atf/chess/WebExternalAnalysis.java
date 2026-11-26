package ro.atf.chess;

/** Current Web integration identity; archived observations retain their original provider. */
final class WebExternalAnalysis {
    private WebExternalAnalysis() { }

    static void requireCurrent(ExternalEngineAnalysis.Provider provider) {
        if (!provider.providerID().equals("stockfish-19.0.1-lite-single") || !provider.version().equals("19.0.1")
                || !provider.buildSha256().equals("ee4f115bdb2d32cadf96cada104a05b5ec257d7ad6f57582190b49bc71f096de")
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
        return batch.best().evaluation(position.turn()).whiteShare();
    }

    static String provider(GameReview review) {
        return review.external() == null ? "{\"providerID\":\"native\",\"label\":\"Chess Lab\"}" : review.external().provider().json();
    }
}
