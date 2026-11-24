package ro.atf.chess;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** Test-only, direct observed-adapter league. No legacy experimental harness or evaluation scope. */
final class HardConfirmationLeague {
    static final long SEED = 93_714_689L;
    static final int CAP = 220, FAMILIES = 10, PER_FAMILY = 10, GAMES = 200;
    static final String START_HEADER = "id\tfamily\tchoice\tprefix";
    static final String DECISION_HEADER = "ply\tactor\tmove\tdepth\tnodes\taccounting\tengine\tstop\tcertificate\tscore\tbound\tpv";
    static final String GAME_HEADER = "id\tfamily\tcolor\tresult\tplies\tunavailable\tdecisions\tcandidateTotal\treferenceCost\treferenceAccounting\tmoves";
    record Start(String id, String family, long choice, String prefix) { }
    record Game(String id, Start start, Side color, ChessMatch match, boolean unavailable,
                long decisions, long candidateTotal, long referenceCost, String referenceAccounting) { }
    record Configuration(HardMasterPolicy candidate, HardMasterPolicy baseline, String reference) {
        Configuration {
            if (!Set.of("BASE_A", "LEGACY").contains(reference)) throw new IllegalArgumentException("Unknown reference");
            if (!candidate.id().equals("hard-a-v6") || candidate.quietTactics() != HardMasterPolicy.QuietTactics.FORCING_PINS
                    || !baseline.id().equals("hard-a-v5") || baseline.quietTactics() != HardMasterPolicy.QuietTactics.NONE)
                throw new IllegalArgumentException("Use the named v6 forcing-pins and v5 baseline policies");
            if (!candidate.limits().equals(baseline.limits()) || candidate.forcingReserve() != baseline.forcingReserve()
                    || !candidate.certificate().equals(baseline.certificate()) || candidate.evaluation() != baseline.evaluation()
                    || candidate.rootPv() != baseline.rootPv() || candidate.pvs() != baseline.pvs()
                    || candidate.moveHints() != baseline.moveHints() || candidate.cutoffOrdering() != baseline.cutoffOrdering())
                throw new IllegalArgumentException("The incremental comparison must preserve budgets and all other mechanisms");
        }
    }
    private HardConfirmationLeague() { }

    static Configuration configuration(String reference) {
        var candidate = HardMasterPolicy.next();
        var baseline = new HardMasterPolicy(candidate.engine(), "hard-a-v5", candidate.limits(), candidate.forcingReserve(),
                candidate.certificate(), candidate.evaluation(), candidate.rootPv(), candidate.pvs(), candidate.moveHints(),
                candidate.cutoffOrdering(), HardMasterPolicy.QuietTactics.NONE);
        return new Configuration(candidate, baseline, reference);
    }

    static String identity(Configuration config) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        Path location = Path.of(HardMaster.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isDirectory(location)) throw new IllegalArgumentException("Use an external compiled classes snapshot");
        try (var files = Files.walk(location)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                digest.update(location.relativize(file).toString().replace('\\', '/').getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(file));
            }
        }
        String harness = harnessFingerprint(Path.of(HardConfirmationLeague.class.getProtectionDomain().getCodeSource().getLocation().toURI()).resolve("ro/atf/chess"));
        return "schema=2\nseed=" + SEED + "\nfamilies=10\nstartsPerFamily=10\ngames=200\ncap=220\n"
                + "candidate=" + config.candidate() + "\nreferenceRole=" + config.reference() + "\nbaseline=" + config.baseline()
                + "\nengineClassesAndResourcesSha256=" + HexFormat.of().formatHex(digest.digest())
                + "\nrunnerAndStatisticsBytecodeSha256=" + harness
                + "\nbook=common, openingChoice preserved per pair\nponder=false\n"
                + "candidateAccounting=TOTAL\nreferenceAccounting=" + (config.reference().equals("BASE_A") ? "TOTAL" : "LEGACY_EXTRA")
                + "\nnoCompleteHintOrReviewAudit=true\nnoPromotionByThisRun=true\n";
    }

    static String harnessFingerprint(Path testLocation) throws Exception {
        var harness = MessageDigest.getInstance("SHA-256");
        var required = new HashSet<>(Set.of("HardConfirmationLeague.class", "HardConfirmationStudyTest.class", "PairedElo.class"));
        try (var files = Files.list(testLocation)) {
            for (Path file : files.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString();
                return name.endsWith(".class") && (name.startsWith("HardConfirmationLeague") || name.startsWith("HardConfirmationStudyTest") || name.startsWith("PairedElo"));
            }).sorted().toList()) {
                required.remove(file.getFileName().toString());
                harness.update(file.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                harness.update(Files.readAllBytes(file));
            }
        }
        if (!required.isEmpty()) throw new IllegalArgumentException("Missing actual runner bytecode " + required);
        return HexFormat.of().formatHex(harness.digest());
    }

    static void freeze(Path out, Configuration config, List<Path> exclusionFiles) throws Exception {
        if (Files.exists(out.resolve("plan.txt"))) throw new IllegalArgumentException("Freeze requires a fresh output folder");
        if (exclusionFiles.isEmpty()) throw new IllegalArgumentException("Supply prior frozen-start ledgers to qualify freshness");
        var excluded = new HashSet<ChessPosition.SearchKey>(); var exclusions = new ArrayList<String>();
        Files.createDirectories(out.resolve("excluded"));
        for (Path file : exclusionFiles) {
            Path frozen = out.resolve("excluded").resolve(String.format(Locale.ROOT, "%03d.tsv", exclusions.size()));
            Files.copy(file, frozen); var rows = Files.readAllLines(frozen); String[] columns = rows.get(0).split("\t");
            int position = -1;
            for (int index = 0; index < columns.length; index++) if (Set.of("uciPosition", "prefix").contains(columns[index])) position = index;
            if (position < 0) throw new IllegalArgumentException("Unknown exclusion schema " + file);
            for (String row : rows.subList(1, rows.size())) {
                String prefix = row.split("\t", -1)[position].replaceFirst("^startpos moves ", "");
                excluded.add(replay(prefix, 0).position().searchKey());
            }
            exclusions.add("excluded/" + frozen.getFileName() + "\t" + hash(frozen));
        }
        var starts = generate(excluded); Files.createDirectories(out);
        var lines = new ArrayList<String>(); lines.add(START_HEADER);
        for (var start : starts) lines.add(start.id() + "\t" + start.family() + "\t" + start.choice() + "\t" + start.prefix());
        Files.write(out.resolve("frozen-starts.tsv"), lines, StandardOpenOption.CREATE_NEW);
        Files.write(out.resolve("exclusions.tsv"), exclusions, StandardOpenOption.CREATE_NEW);
        Files.writeString(out.resolve("plan.txt"), identity(config) + "startsSha256=" + hash(out.resolve("frozen-starts.tsv"))
                + "\nexclusionsSha256=" + hash(out.resolve("exclusions.tsv")) + "\n", StandardOpenOption.CREATE_NEW);
    }

    static List<Start> generate(Set<ChessPosition.SearchKey> excluded) {
        var families = List.of(
                "english|c2c4 c7c5 b1c3 b8c6 g2g3 g7g6", "pirc|e2e4 d7d6 d2d4 g8f6 b1c3 g7g6 f2f4",
                "nimzo|d2d4 g8f6 c2c4 e7e6 b1c3 f8b4 e2e3", "kings-gambit|e2e4 e7e5 f2f4 e5f4 g1f3 g7g5",
                "slav|d2d4 d7d5 c2c4 c7c6 g1f3 g8f6 b1c3 d5c4", "catalan|d2d4 g8f6 c2c4 e7e6 g2g3 d7d5 f1g2 f8e7",
                "vienna|e2e4 e7e5 b1c3 g8f6 f2f4 d7d5", "petroff|e2e4 e7e5 g1f3 g8f6 f3e5 d7d6",
                "benoni|d2d4 g8f6 c2c4 c7c5 d4d5 e7e6 b1c3 e6d5 c4d5", "reti|g1f3 d7d5 c2c4 d5d4");
        var starts = new ArrayList<Start>(); var seen = new HashSet<>(excluded); int familyIndex = 0;
        for (String definition : families) {
            String[] family = definition.split("\\|");
            for (int sample = 0; sample < PER_FAMILY; sample++) {
                boolean accepted = false;
                for (int attempt = 0; attempt < 100 && !accepted; attempt++) {
                    long choice = SEED + 104_729L * familyIndex + 7_919L * sample + attempt;
                    var random = new SplittableRandom(choice); var game = replay(family[1], choice);
                    for (int step = 0; step < 4 + sample % 3 && game.result() == ChessMatch.Result.IN_PROGRESS; step++) {
                        var board = game.position();
                        var legal = board.legalMoves().stream().sorted(Comparator.comparing(Move::uci)).toList();
                        var quiet = legal.stream().filter(move -> board.pieceAt(move.to()) == null
                                && !(board.pieceAt(move.from()).kind() == ChessPosition.Kind.PAWN && move.from() % 8 != move.to() % 8)
                                && !board.play(move).isCheck()).toList();
                        var choices = quiet.isEmpty() ? legal : quiet;
                        game = game.play(choices.get(random.nextInt(choices.size())));
                    }
                    if (game.result() == ChessMatch.Result.IN_PROGRESS && seen.add(game.position().searchKey())) {
                        starts.add(new Start(family[0] + "-" + sample, family[0], choice, moves(game))); accepted = true;
                    }
                }
                if (!accepted) throw new IllegalStateException("Cannot generate a fresh legal start");
            }
            familyIndex++;
        }
        return List.copyOf(starts);
    }

    static List<Start> load(Path out, Configuration config) throws Exception {
        String expected = identity(config) + "startsSha256=" + hash(out.resolve("frozen-starts.tsv"))
                + "\nexclusionsSha256=" + hash(out.resolve("exclusions.tsv")) + "\n";
        if (!Files.readString(out.resolve("plan.txt")).equals(expected)) throw new IllegalArgumentException("Source, policy or frozen corpus changed");
        for (String item : Files.readAllLines(out.resolve("exclusions.tsv"))) {
            var fields = item.split("\t", -1); Path excluded = out.resolve(fields[0]).normalize();
            if (fields.length != 2 || !excluded.startsWith(out.normalize()) || !hash(excluded).equals(fields[1]))
                throw new IllegalArgumentException("Frozen exclusions changed");
        }
        var lines = Files.readAllLines(out.resolve("frozen-starts.tsv"));
        if (!lines.get(0).equals(START_HEADER)) throw new IllegalArgumentException("Bad start schema");
        var starts = new ArrayList<Start>(); var seen = new HashSet<ChessPosition.SearchKey>(); var ids = new HashSet<String>();
        for (String line : lines.subList(1, lines.size())) {
            var row = line.split("\t", -1); if (row.length != 4 || !ids.add(row[0])) throw new IllegalArgumentException("Repeated or malformed start");
            var start = new Start(row[0], row[1], Long.parseLong(row[2]), row[3]); var game = replay(start.prefix(), start.choice());
            if (game.result() != ChessMatch.Result.IN_PROGRESS || !seen.add(game.position().searchKey())) throw new IllegalArgumentException("Terminal or equivalent start");
            starts.add(start);
        }
        var families = starts.stream().collect(Collectors.groupingBy(Start::family, LinkedHashMap::new, Collectors.counting()));
        if (starts.size() != 100 || families.size() != 10 || families.values().stream().anyMatch(count -> count != 10)) throw new IllegalArgumentException("Expected ten families of ten starts");
        return List.copyOf(starts);
    }

    static void run(Path out, Configuration config, int workers) throws Exception {
        if (workers < 1 || workers > 4) throw new IllegalArgumentException("Use one to four workers");
        var starts = load(out, config); var tasks = new ArrayList<Callable<Game>>();
        for (var start : starts) for (var color : Side.values()) tasks.add(() -> play(out, config, start, color));
        var games = new ArrayList<Game>();
        try (var pool = Executors.newFixedThreadPool(workers)) { for (var future : pool.invokeAll(tasks)) games.add(future.get()); }
        writeSummary(out, config, games); verify(out, config);
    }

    private static Game play(Path out, Configuration config, Start start, Side color) throws Exception {
        String id = start.id() + "-" + color; Path folder = out.resolve("games").resolve(id); Files.createDirectories(folder);
        Path ledger = folder.resolve("decisions.tsv");
        if (!Files.exists(ledger)) Files.writeString(ledger, DECISION_HEADER + "\n", StandardOpenOption.CREATE_NEW);
        var restored = readGame(out, config, start, color, false); var game = restored.match(); boolean unavailable = restored.unavailable();
        while (!unavailable && game.result() == ChessMatch.Result.IN_PROGRESS && game.ply() < CAP) {
            boolean candidate = game.position().turn() == color; String actor = candidate ? "CANDIDATE" : "REFERENCE";
            ChessMatch.BotDecision decision; HardMaster.Report report = null;
            if (!candidate && config.reference().equals("LEGACY")) decision = game.referenceDecision(MatchSetup.Level.HARD);
            else {
                var observed = HardMaster.decideObserved(game, candidate ? config.candidate() : config.baseline(), true);
                decision = observed.decision(); report = observed.report();
            }
            var result = report == null ? null : report.result();
            if (decision == null && result == null) throw new IllegalStateException("An unavailable decision still needs an observed search receipt");
            String move = decision == null ? "_" : decision.move().uci();
            String engine = decision == null ? (candidate ? config.candidate().id() : config.baseline().id()) : decision.engine();
            long nodes = decision == null ? result.nodes() : decision.searchedNodes();
            String accounting = decision == null ? "TOTAL" : decision.accounting().name();
            String stop = decision == null ? result.stop().name() : decision.stop();
            String certificate = report == null ? "NOT_RUN" : report.certificateReceipt();
            String pv = result == null ? (decision == null ? "_" : move) : result.line().stream().map(Move::uci).collect(Collectors.joining(" "));
            if (pv.isEmpty()) pv = "_";
            String row = game.ply() + "\t" + actor + "\t" + move + "\t" + (decision == null ? result.completedDepth() : decision.depth())
                    + "\t" + nodes + "\t" + accounting + "\t" + engine + "\t" + stop + "\t" + certificate
                    + "\t" + (result == null || result.score() == null ? "_" : result.score())
                    + "\t" + (result == null || result.bound() == null ? "ABSENT" : result.bound().name()) + "\t" + pv;
            Files.writeString(ledger, row + "\n", StandardOpenOption.APPEND);
            if (decision == null) unavailable = true; else game = game.play(decision.move());
        }
        var completed = readGame(out, config, start, color, false);
        var rows = List.of(GAME_HEADER, gameRow(completed)); Path receipt = folder.resolve("game.tsv");
        if (Files.exists(receipt) && !Files.readAllLines(receipt).equals(rows)) throw new IllegalStateException("Existing outcome differs from its decision ledger");
        if (!Files.exists(receipt)) Files.write(receipt, rows, StandardOpenOption.CREATE_NEW);
        Path pgn = folder.resolve("game.pgn"); String rendered = pgn(completed);
        if (Files.exists(pgn) && !Files.readString(pgn).equals(rendered)) throw new IllegalStateException("PGN differs from the legal game");
        if (!Files.exists(pgn)) Files.writeString(pgn, rendered, StandardOpenOption.CREATE_NEW);
        System.out.println("CONFIRMATION " + id + " " + completed.match().result() + " ply=" + completed.match().ply());
        return completed;
    }

    static Game readGame(Path out, Configuration config, Start start, Side color, boolean requireOutcome) throws Exception {
        String id = start.id() + "-" + color; Path folder = out.resolve("games").resolve(id);
        var rows = Files.readAllLines(folder.resolve("decisions.tsv"));
        if (!rows.get(0).equals(DECISION_HEADER)) throw new IllegalArgumentException("Wrong decision schema");
        var game = replay(start.prefix(), start.choice()); boolean unavailable = false; long candidateCost = 0, referenceCost = 0;
        String referenceAccounting = config.reference().equals("BASE_A") ? "TOTAL" : "LEGACY_EXTRA";
        for (String text : rows.subList(1, rows.size())) {
            if (unavailable || game.result() != ChessMatch.Result.IN_PROGRESS || game.ply() >= CAP) throw new IllegalArgumentException("Action after terminal or unavailable disposition");
            var row = text.split("\t", -1); if (row.length != 12 || Integer.parseInt(row[0]) != game.ply()) throw new IllegalArgumentException("Missing, repeated or malformed decision");
            boolean candidate = game.position().turn() == color; String actor = candidate ? "CANDIDATE" : "REFERENCE";
            String accounting = candidate ? "TOTAL" : referenceAccounting;
            String engine = candidate ? config.candidate().id() : config.reference().equals("BASE_A") ? config.baseline().id() : "legacy-v1";
            if (!row[1].equals(actor) || !row[5].equals(accounting) || !row[6].equals(engine)) throw new IllegalArgumentException("Foreign actor, policy or accounting");
            long nodes = Long.parseLong(row[4]); int depth = Integer.parseInt(row[3]);
            long cap = candidate ? config.candidate().maximumDecisionNodes() : config.reference().equals("BASE_A") ? config.baseline().maximumDecisionNodes() : Long.MAX_VALUE;
            if (nodes < 0 || nodes > cap || depth < 0) throw new IllegalArgumentException("Invalid cost or horizon");
            if (candidate) candidateCost += nodes; else referenceCost += nodes;
            boolean observedA = candidate || config.reference().equals("BASE_A");
            if (!observedA || row[7].equals("BOOK")) {
                if (!row[9].equals("_") || !row[10].equals("ABSENT")) throw new IllegalArgumentException("Uncaptured BOOK/LEGACY scores and bounds must be absent");
            } else if (!row[2].equals("_")) {
                if (!row[10].equals("EXACT") || row[9].equals("_") || row[11].equals("_") || depth < 1)
                    throw new IllegalArgumentException("A public played score requires its actual exact bound and PV");
                Integer.parseInt(row[9]);
            } else if (!Set.of("ABSENT", "EXACT", "LOWER", "UPPER").contains(row[10])) throw new IllegalArgumentException("Unknown observed bound");
            if (!row[11].equals("_")) {
                var board = game.position(); var line = row[11].split(" ");
                if (!row[2].equals(line[0])) throw new IllegalArgumentException("PV root differs from its decision");
                for (String move : line) board = board.play(Move.fromUci(move));
            } else if (!row[2].equals("_")) throw new IllegalArgumentException("A played decision needs at least its root PV");
            if (row[2].equals("_")) unavailable = true;
            else {
                var move = Move.fromUci(row[2]);
                if (row[7].equals("BOOK") && (!move.equals(game.hardBookMove()) || nodes != 0 || depth != 0)) throw new IllegalArgumentException("False common-book receipt");
                game = game.play(move);
            }
        }
        var result = new Game(id, start, color, game, unavailable, rows.size() - 1, candidateCost, referenceCost, referenceAccounting);
        if (requireOutcome) {
            if (!unavailable && game.result() == ChessMatch.Result.IN_PROGRESS && game.ply() != CAP) throw new IllegalArgumentException("Incomplete game presented as a completed task");
            if (!Files.readAllLines(folder.resolve("game.tsv")).equals(List.of(GAME_HEADER, gameRow(result)))) throw new IllegalArgumentException("Outcome or cost does not reconstruct");
            if (!Files.readString(folder.resolve("game.pgn")).equals(pgn(result))) throw new IllegalArgumentException("PGN does not reconstruct");
        }
        return result;
    }

    static void verify(Path out, Configuration config) throws Exception {
        var starts = load(out, config); var games = new ArrayList<Game>();
        for (var start : starts) for (var color : Side.values()) games.add(readGame(out, config, start, color, true));
        try (var folders = Files.list(out.resolve("games"))) {
            var actual = folders.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
            requireRoster(starts, actual);
        }
        var aggregate = new ArrayList<String>(); aggregate.add(GAME_HEADER); games.forEach(game -> aggregate.add(gameRow(game)));
        if (!Files.readAllLines(out.resolve("games.tsv")).equals(aggregate)) throw new IllegalArgumentException("Aggregate omits or substitutes paired games");
        requireSummary(config, games, Files.readAllLines(out.resolve("summary.tsv")));
        if (games.size() != GAMES || games.stream().anyMatch(Game::unavailable)) throw new IllegalArgumentException("Availability or coverage gate failed");
    }

    private static void writeSummary(Path out, Configuration config, List<Game> games) throws Exception {
        var rows = new ArrayList<String>(); rows.add(GAME_HEADER); games.forEach(game -> rows.add(gameRow(game)));
        writeSameOrNew(out.resolve("games.tsv"), rows); writeSameOrNew(out.resolve("summary.tsv"), summary(config, games));
        String pgn = games.stream().map(HardConfirmationLeague::pgn).collect(Collectors.joining("\n"));
        Path file = out.resolve("league.pgn");
        if (Files.exists(file) && !Files.readString(file).equals(pgn)) throw new IllegalArgumentException("Do not overwrite mismatched PGN");
        if (!Files.exists(file)) Files.writeString(file, pgn, StandardOpenOption.CREATE_NEW);
    }
    private static void writeSameOrNew(Path file, List<String> lines) throws IOException {
        if (Files.exists(file)) { if (!Files.readAllLines(file).equals(lines)) throw new IllegalArgumentException("Do not overwrite mismatched evidence: " + file); }
        else Files.write(file, lines, StandardOpenOption.CREATE_NEW);
    }
    static void requireSummary(Configuration config, List<Game> games, List<String> rows) {
        if (!rows.equals(summary(config, games))) throw new IllegalArgumentException("Elo, CI, sensitivity or cost does not reconstruct");
    }
    static List<String> summary(Configuration config, List<Game> games) {
        var families = new LinkedHashMap<String, List<PairedElo.Pair>>(); int[] totals = new int[4]; long candidate = 0, reference = 0;
        for (int index = 0; index < games.size(); index += 2) {
            var one = games.get(index); var two = games.get(index + 1);
            if (!one.start().equals(two.start()) || one.color() != Side.WHITE || two.color() != Side.BLACK) throw new IllegalArgumentException("Mismatched color pair");
            int[] pair = new int[4];
            for (var game : List.of(one, two)) {
                int outcome = outcome(game); pair[outcome]++; totals[outcome]++; candidate += game.candidateTotal(); reference += game.referenceCost();
            }
            families.computeIfAbsent(one.start().family(), ignored -> new ArrayList<>()).add(new PairedElo.Pair(pair[0], pair[1], pair[2], pair[3]));
        }
        var estimate = PairedElo.estimateRepeated(List.copyOf(families.values()), 10_000, 30_271);
        String value = String.format(Locale.ROOT, "%d\t%d\t%d\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%d\t%d\t%s",
                games.size()/2, totals[0], totals[1], totals[2], totals[3], estimate.eloMidpoint(), estimate.eloLow95(), estimate.eloHigh95(),
                PairedElo.elo(estimate.scoreLow()), PairedElo.elo(estimate.scoreHigh()), candidate, reference,
                config.reference().equals("BASE_A") ? "TOTAL" : "LEGACY_EXTRA");
        return List.of("pairs\tW\tD\tL\tunfinished\tdeltaElo\tclusterLow95\tclusterHigh95\tunfinishedLow\tunfinishedHigh\tcandidateTotalNodes\treferenceReportedNodes\treferenceAccounting", value);
    }
    static int outcome(Game game) {
        if (game.match().result() == ChessMatch.Result.IN_PROGRESS) return 3;
        String won = game.color() == Side.WHITE ? "WHITE_WINS" : "BLACK_WINS";
        return game.match().result().name().equals(won) ? 0 : game.match().result().name().endsWith("_WINS") ? 2 : 1;
    }
    static String gameRow(Game game) { return game.id() + "\t" + game.start().family() + "\t" + game.color() + "\t" + game.match().result()
            + "\t" + game.match().ply() + "\t" + game.unavailable() + "\t" + game.decisions() + "\t" + game.candidateTotal()
            + "\t" + game.referenceCost() + "\t" + game.referenceAccounting() + "\t" + moves(game.match()); }
    static String pgn(Game game) {
        String result = switch (game.match().result()) { case WHITE_WINS -> "1-0"; case BLACK_WINS -> "0-1"; case IN_PROGRESS -> "*"; default -> "1/2-1/2"; };
        String candidate = "Hard A v6", reference = game.referenceAccounting().equals("TOTAL") ? "Hard A v5 baseline" : "Legacy Hard";
        var text = new StringBuilder("[Event \"J7 incremental Hard confirmation\"]\n[Site \"Offline\"]\n[Date \"????.??.??\"]\n[Round \"").append(game.id())
                .append("\"]\n[White \"").append(game.color() == Side.WHITE ? candidate : reference)
                .append("\"]\n[Black \"").append(game.color() == Side.BLACK ? candidate : reference)
                .append("\"]\n[Result \"").append(result).append("\"]\n\n");
        var history = game.match().moveHistory();
        for (int index = 0; index < history.size(); index++) { if (index % 2 == 0) text.append(index/2+1).append(". "); text.append(san(game.match().positionAt(index), history.get(index))).append(' '); }
        return text.append(result).append("\n").toString();
    }
    static void requireRoster(List<Start> starts, Set<String> actual) {
        var expected = new HashSet<String>();
        for (var start : starts) for (var side : Side.values()) {
            if (!expected.add(start.id() + "-" + side)) throw new IllegalArgumentException("Duplicate frozen identity");
        }
        if (expected.isEmpty() || !expected.equals(actual)) throw new IllegalArgumentException("Foreign or missing color pair");
    }
    static String san(ChessPosition board, Move move) {
        if (!board.legalMoves().contains(move)) throw new IllegalArgumentException("Illegal SAN move");
        var piece = board.pieceAt(move.from()); var kind = piece.kind(); String text;
        if (kind == ChessPosition.Kind.KING && Math.abs(move.to() - move.from()) == 2) text = move.to() > move.from() ? "O-O" : "O-O-O";
        else {
            var label = new StringBuilder();
            boolean capture = board.pieceAt(move.to()) != null || kind == ChessPosition.Kind.PAWN && move.from()%8 != move.to()%8;
            if (kind == ChessPosition.Kind.PAWN) { if (capture) label.append((char)('a' + move.from()%8)); }
            else {
                label.append("PNBRQK".charAt(kind.ordinal()));
                var others = board.legalMoves().stream().filter(other -> other.from() != move.from() && other.to() == move.to()
                        && board.pieceAt(other.from()).kind() == kind).toList();
                if (!others.isEmpty()) {
                    boolean file = others.stream().anyMatch(other -> other.from()%8 == move.from()%8);
                    boolean rank = others.stream().anyMatch(other -> other.from()/8 == move.from()/8);
                    if (!file || rank) label.append((char)('a' + move.from()%8));
                    if (file) label.append((char)('1' + move.from()/8));
                }
            }
            if (capture) label.append('x'); label.append(move.uci(), 2, 4);
            if (move.promotion() != null) label.append('=').append("PNBRQK".charAt(move.promotion().ordinal()));
            text = label.toString();
        }
        var after = board.play(move);
        return text + (after.isCheckmate() ? "#" : after.isCheck() ? "+" : "");
    }
    static ChessMatch replay(String prefix, long choice) {
        var game = ChessMatch.start(new MatchSetup(Side.WHITE, MatchSetup.Level.HARD, false), choice);
        if (!prefix.isBlank()) for (String move : prefix.split(" ")) game = game.play(Move.fromUci(move));
        return game;
    }
    static String moves(ChessMatch game) { return game.moveHistory().stream().map(Move::uci).collect(Collectors.joining(" ")); }
    static String hash(Path file) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))); }
}