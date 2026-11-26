package ro.atf.chess;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Imports one declared calibration game. File integrity and semantic replay are separate checks. */
final class AiProfileEvidence {
    record Game(AiProfileRating.Game rating, ChessMatch finalMatch, int nativeCalls, int hintCalls,
                long extraNodes, boolean admittedToLearning) { }

    static Game read(Path directory, String runId, ExternalEngineAnalysis.Provider coach) throws Exception {
        var json = LearningGameAudit.mapper();
        var receipt = json.readTree(Files.readString(directory.resolve("complete.json")));
        require(receipt.path("nativeExit").isInt() && receipt.path("nativeExit").asInt() == 0,
                "Missing successful original completion");
        require(runId.equals(receipt.path("runId").asText()), "Foreign calibration run");
        for (String name : List.of("steps", "pgn")) {
            String file = name.equals("steps") ? "steps.jsonl" : "game.pgn";
            bound(directory.resolve(file), receipt.path(name + "Sha256").asText());
        }
        var level = MatchSetup.Level.valueOf(receipt.path("level").asText());
        var side = ChessPosition.Side.valueOf(receipt.path("nativeSide").asText());
        var start = receipt.path("start");
        boolean audited = start.path("audit").asBoolean();
        require(receipt.path("family").asText().equals(start.path("family").asText()), "Foreign opening family");
        String id = level.name().toLowerCase() + "-" + start.path("id").asText() + "-" + side.name().toLowerCase();
        require(id.equals(receipt.path("id").asText()), "Foreign game identity");
        var game = ChessMatch.start(new MatchSetup(side.other(), level, false), start.path("choice").asLong());
        for (var move : start.path("prefix")) game = game.play(ChessPosition.Move.fromUci(move.asText()));
        int first = game.ply(), calls = 0, hints = 0, failedHints = 0;
        long work = 0;
        require(first == receipt.path("startPlies").asInt(-1), "Changed declared starting prefix");
        try (var rows = Files.lines(directory.resolve("steps.jsonl"))) {
            for (var iterator = rows.iterator(); iterator.hasNext();) {
                var row = json.readTree(iterator.next());
                require(runId.equals(row.path("runId").asText()) && id.equals(row.path("gameId").asText()), "Foreign move phase");
                require(game.result() == ChessMatch.Result.IN_PROGRESS, "Move after a terminal result");
                require(row.path("plyBefore").asInt(-1) == game.ply()
                        && row.path("prefix").asText().equals(moves(game))
                        && row.path("sideToMove").asText().equals(game.position().turn().name())
                        && row.path("nativeLevel").asText().equals(level.name()), "Wrong move context or history");
                var move = ChessPosition.Move.fromUci(row.path("played").asText());
                if (game.position().turn() == side) {
                    require("NATIVE".equals(row.path("actor").asText()), "Foreign actor at Native turn");
                    var observed = HardQuestAudit.read(row.path("native"));
                    validateNative(game, level, observed);
                    require(move.equals(observed.decision().move()), "Played move differs from actual Native observation");
                    work += observed.decision().extraNodes();
                    calls++;
                } else {
                    require("ANCHOR".equals(row.path("actor").asText()), "Foreign actor at anchor turn");
                    var anchor = row.path("anchor");
                    require(move.uci().equals(anchor.path("move").asText()), "Changed anchor choice");
                    var raw = new java.util.ArrayList<String>();
                    anchor.path("raw").forEach(value -> raw.add(value.asText()));
                    require(!raw.isEmpty() && raw.getFirst().equals("> position startpos moves " + moves(game))
                            && raw.contains("> go nodes 6000")
                            && raw.getLast().startsWith("< bestmove " + move.uci()), "Missing original anchor search");
                }
                if (audited) {
                    if (row.has("hint")) {
                        require(!row.has("hintFailure"), "Advice cannot be both complete and unavailable");
                        var hint = ExternalEngineAnalysis.batch(game, game.ply(), row.path("hint").toString());
                        require(coach.equals(hint.provider()), "Foreign coaching provider or options");
                        hints++;
                    } else {
                        require(row.path("hintFailure").path("type").asText().startsWith("java.")
                                && !row.path("hintFailure").path("message").asText().isBlank(), "Missing typed advice failure");
                        failedHints++;
                    }
                } else require(!row.has("hint"), "Undeclared learning evidence");
                game = game.play(move);
            }
        }
        require(game.ply() > first && game.ply() == receipt.path("plies").asInt(-1), "Empty or incomplete played game");
        require(game.checkpoint().equals(receipt.path("checkpoint").asText()), "Changed final checkpoint");
        int score = score(game.result(), side);
        require(score == receipt.path("scoreTwice").asInt(-9) && result(game.result()).equals(receipt.path("result").asText()),
                "An unfinished or lost game cannot be relabelled drawn");
        require(calls == receipt.path("nativeDecisions").asInt(-1)
                && hints == receipt.path("hintsBeforeActualMoves").asInt(-1)
                && failedHints == receipt.path("failedHintsBeforeActualMoves").asInt(0)
                && work == receipt.path("legacyExtraNodes").asLong(-1), "Missing Native work or pre-move observations");
        boolean completeReview = false;
        if (audited) {
            bound(directory.resolve("coaching.jsonl"), receipt.path("coachingSha256").asText());
            require(hints + failedHints == game.ply() - first, "Missing pre-move advice attempts");
            if (!receipt.path("reviewFailure").isMissingNode() && !receipt.path("reviewFailure").isNull()) {
                bound(directory.resolve("review-failure.json"), receipt.path("reviewFailureSha256").asText());
                var failure = json.readTree(Files.readString(directory.resolve("review-failure.json")));
                require(runId.equals(failure.path("runId").asText()) && game.checkpoint().equals(failure.path("checkpoint").asText())
                        && failure.path("error").asText().equals(receipt.path("reviewFailure").asText())
                        && !Files.exists(directory.resolve("review.json")), "Changed final-review failure");
            } else {
                bound(directory.resolve("review.json"), receipt.path("reviewSha256").asText());
                var review = ExternalEngineAnalysis.review(game, Files.readString(directory.resolve("review.json")));
                require(review.provider().equals(coach) && review.rated() == (score >= 0), "Foreign or wrongly rated final coaching review");
                completeReview = true;
            }
        }
        boolean admitted = audited && failedHints == 0 && completeReview;
        require(receipt.path("learningAdmission").asText().equals(audited
                ? admitted ? "COMPLETE_LITE_HINTS_AND_FINAL_REVIEW" : "NOT_ADMITTED_INCOMPLETE_COACHING"
                : "NOT_ADMITTED_NO_COMPLETE_COACHING"), "Wrong learning admission");
        return new Game(new AiProfileRating.Game(id, start.path("id").asText(), start.path("family").asText(), side.name(), score),
                game, calls, hints, work, admitted);
    }

    static void validateNative(ChessMatch game, MatchSetup.Level level, ChessMatch.BotAnalysis observed) {
        var decision = observed.decision();
        var search = observed.search();
        require(decision.move() != null && game.position().legalMoves().contains(decision.move())
                && decision.depth() == search.depth() && decision.extraNodes() == search.extraNodes()
                && decision.extraNodes() >= 0 && !search.result().ranked().isEmpty(), "Incomplete Native observation");
        require(decision.move().equals(selected(game, level, search.result())), "Native root differs from history-aware selection");
        for (var line : search.result().ranked()) {
            var end = game.position();
            for (var move : line.line()) end = end.play(move);
            require(!line.line().isEmpty() && line.materialChange() == HintAuditSession.materialChangeCp(game.position(), end), "Changed Native PV material");
        }
        require(decision.extraNodes() <= (level == MatchSetup.Level.HARD ? HardQuestAudit.maximumNodes(game.position())
                : MatchAiPolicy.CURRENT.opponent(level).extraNodes()), "Unaccounted Native allowance");
        // Legacy capture verification can emit a PV beyond the ordinary depth without a typed certificate.
        // Its legal moves can support a game result, never a claim of a complete adversarial proof.
        if (search.pawnProof() != null) HardQuestAudit.validate(game, observed);
    }

    static ChessPosition.Move selected(ChessMatch game, MatchSetup.Level level, TacticalAnalysis.Result result) {
        ChessPosition.Move choice = null;
        int fewest = Integer.MAX_VALUE, activity = Integer.MIN_VALUE;
        for (var line : result.best()) {
            int visits = visits(game, game.position().play(line.move()), game.ply());
            int value = ChessMatch.activity(game.position(), line.move());
            if (visits < fewest || visits == fewest && value > activity) {
                choice = line.move(); fewest = visits; activity = value;
            }
        }
        if (level != MatchSetup.Level.HARD) return choice;
        boolean cycle = visits(game, game.position(), game.ply() - 1) >= 2;
        var leader = result.ranked().getFirst();
        if ((leader.score() < 0 || cycle && leader.score() == 0) && game.searchQuietPlies() >= 15) {
            for (var move : game.position().legalMoves())
                if (visits(game, game.position().play(move), game.ply()) >= 4) return move;
        } else if (cycle && fewest > 0 && leader.ending() == TacticalAnalysis.Ending.HORIZON) {
            var policy = MatchAiPolicy.CURRENT.repetition;
            int floor = Math.max(policy.minAdvantageCp(), leader.score() - policy.maxScoreLossCp());
            for (var line : result.ranked())
                if (line.score() >= floor && visits(game, game.position().play(line.move()), game.ply()) < fewest) return line.move();
        }
        return choice;
    }

    private static int visits(ChessMatch game, ChessPosition position, int last) {
        int count = 0;
        for (int ply = last; ply >= game.ply() - game.searchQuietPlies(); ply--)
            if (position.sameForRepetition(game.positionAt(ply))) count++;
        return count;
    }

    static void bound(Path file, String sha256) throws Exception {
        require(sha256.matches("[a-f0-9]{64}") && Files.isRegularFile(file)
                && HardLeagueAudit.digest(Files.readAllBytes(file)).equals(sha256), "Missing or changed evidence: " + file.getFileName());
    }
    static String moves(ChessMatch game) {
        var moves = new java.util.ArrayList<String>();
        for (int ply = 1; ply <= game.ply(); ply++) moves.add(game.moveAt(ply).uci());
        return String.join(" ", moves);
    }
    static int score(ChessMatch.Result result, ChessPosition.Side side) {
        return switch (result) {
            case IN_PROGRESS -> -1;
            case WHITE_WINS -> side == ChessPosition.Side.WHITE ? 2 : 0;
            case BLACK_WINS -> side == ChessPosition.Side.BLACK ? 2 : 0;
            default -> 1;
        };
    }
    static String result(ChessMatch.Result result) {
        return switch (result) {
            case WHITE_WINS -> "1-0";
            case BLACK_WINS -> "0-1";
            case IN_PROGRESS -> "*";
            default -> "1/2-1/2";
        };
    }
    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalArgumentException(reason);
    }
    private AiProfileEvidence() { }
}
