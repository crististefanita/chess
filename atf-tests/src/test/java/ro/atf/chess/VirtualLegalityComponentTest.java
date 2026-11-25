package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.yaml.snakeyaml.Yaml;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class VirtualLegalityComponentTest {
    private static final Field OCCUPIED = occupiedField(ChessPosition.class);
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final List<String> FENS = List.of(
            START,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1",
            "4k3/8/8/r4pPK/8/8/8/8 w - f6 0 1",
            "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1",
            "4k3/8/8/8/3Pp3/8/8/4K3 b - d3 0 1",
            "4k3/8/8/8/8/8/8/R3K2r w Q - 0 1",
            "2k2r2/8/8/8/8/8/8/R3K2R w KQ - 0 1",
            "r3k2r/8/8/8/8/8/8/2K2R2 b kq - 0 1",
            "7k/P7/8/8/8/8/8/K7 w - - 0 1",
            "k7/8/8/8/8/8/p7/7K b - - 0 1",
            "7k/8/8/8/8/8/4r3/4K3 w - - 0 1",
            "8/8/8/8/8/4k3/8/4K3 w - - 0 1",
            "7k/8/8/8/8/8/1r6/K7 w - - 0 1",
            "2k3r1/8/8/8/8/8/8/R3K2R w KQ - 0 1",
            "1k6/8/8/8/8/8/8/R2NK2R w KQ - 0 1");

    @Test void orderedMoveTreesMatchTheFrozenWholePositionOracle(Checks check) {
        long[] known = {8_902, 97_862, 62_379, 43_238};
        for (int i = 0; i < 4; i++) {
            int depth = i == 3 ? 4 : 3;
            long leaves = tree(ChessPosition.fromFen(FENS.get(i)), LegalityReferencePosition.fromFen(FENS.get(i)), depth, FENS.get(i));
            check.same("Exact ordered tree and known perft fixture " + i + " at depth " + depth, known[i], leaves);
        }
        for (int i = 4; i < FENS.size(); i++) {
            String fen = FENS.get(i);
            long leaves = tree(ChessPosition.fromFen(fen), LegalityReferencePosition.fromFen(fen), 2, fen);
            check.same("Special position " + i + " retains ordered moves, checks, keys and resulting pieces", true, leaves >= 0);
        }
    }

    @Test void seededWalksRetainStateOrderAndImmutability(Checks check) {
        var random = new Random(0x800d1a527df3d8f0L);
        int visited = 0;
        for (int game = 0; game < 24; game++) {
            var actual = ChessPosition.fromFen(FENS.get(game % 4));
            var expected = LegalityReferencePosition.fromFen(FENS.get(game % 4));
            for (int ply = 0; ply < 100; ply++) {
                String label = "walk " + game + ", ply " + ply;
                equivalent(actual, expected, label);
                visited++;
                var moves = actual.legalMoves();
                if (moves.isEmpty()) break;
                int choice = random.nextInt(moves.size());
                var before = snapshot(actual);
                var next = actual.play(moves.get(choice));
                require(before.equals(snapshot(actual)), label + " mutated its parent");
                actual = next;
                expected = expected.play(expected.legalMoves().get(choice));
            }
        }
        check.same("Seeded differential replay covered over 1500 complete positions", true, visited > 1500);
        var initial = ChessPosition.initial();
        check.rejects("The public legal-move list remains immutable", UnsupportedOperationException.class,
                () -> initial.legalMoves().clear());
        check.rejects("The public per-square list remains immutable", UnsupportedOperationException.class,
                () -> initial.legalMovesFrom(BoardSquare.parse("e2")).clear());
    }

    @Test void illegalPublicMovesAndAllPromotionChoicesKeepTheirContracts(Checks check) {
        var initial = ChessPosition.initial();
        for (var move : List.of(new ChessPosition.Move(-1, 0), new ChessPosition.Move(0, 64),
                new ChessPosition.Move(12, 12), ChessPosition.Move.fromUci("e7e5"),
                ChessPosition.Move.fromUci("e2e4q"), ChessPosition.Move.fromUci("e1e8"))) {
            check.rejects("Public play rejects " + move, IllegalArgumentException.class, () -> initial.play(move));
        }
        var promotion = ChessPosition.fromFen("1r5k/P7/8/8/8/8/8/K7 w - - 0 1");
        var promotions = promotion.legalMovesFrom(BoardSquare.parse("a7"));
        check.same("Quiet and capture promotions retain their exact configured order",
                List.of("a7a8q", "a7a8r", "a7a8b", "a7a8n", "a7b8q", "a7b8r", "a7b8b", "a7b8n"),
                promotions.stream().map(ChessPosition.Move::uci).toList());
        for (var move : promotions) {
            check.same("Promotion places the requested piece for " + move.uci(),
                    new ChessPosition.Piece(ChessPosition.Side.WHITE, move.promotion()), promotion.play(move).pieceAt(move.to()));
        }
        for (var kind : new ChessPosition.Kind[]{null, ChessPosition.Kind.PAWN, ChessPosition.Kind.KING}) {
            check.rejects("Promotion rejects missing or forbidden kind " + kind, IllegalArgumentException.class,
                    () -> promotion.play(new ChessPosition.Move(48, 56, kind)));
        }
        check.same("En passant cannot remove two blockers and expose the own king", false,
                ChessPosition.fromFen(FENS.get(6)).legalMoves().contains(ChessPosition.Move.fromUci("g5f6")));
        check.same("King capture removes the former checking piece", true,
                ChessPosition.fromFen(FENS.get(14)).legalMoves().contains(ChessPosition.Move.fromUci("e1e2")));
        check.same("The two kings cannot become adjacent", false,
                ChessPosition.fromFen(FENS.get(15)).legalMoves().contains(ChessPosition.Move.fromUci("e1e2")));
        check.same("Castling cannot cross an attacked square", false,
                ChessPosition.fromFen(FENS.get(10)).legalMoves().contains(ChessPosition.Move.fromUci("e1g1")));
        check.same("Castling cannot finish on an attacked square", false,
                ChessPosition.fromFen(FENS.get(17)).legalMoves().contains(ChessPosition.Move.fromUci("e1g1")));
        check.same("Castling cannot pass through an own blocker", false,
                ChessPosition.fromFen(FENS.get(18)).legalMoves().contains(ChessPosition.Move.fromUci("e1c1")));
    }

    @Test void acceptedYamlMovementVariantsMatchTheOriginalSemantics(Checks check) throws Exception {
        String standard;
        try (var stream = ChessResourceLoader.open("chess-rules.yaml")) {
            standard = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        var variants = List.of(
                standard.replace("KNIGHT: {mode: leap, vectors: [[1, 2], [2, 1], [2, -1], [1, -2], [-1, -2], [-2, -1], [-2, 1], [-1, 2]]}",
                        "KNIGHT: {mode: leap, vectors: [[1, 0], [-1, 0]]}"),
                standard.replace("BISHOP: {mode: ray, vectors: [[1, 1], [1, -1], [-1, -1], [-1, 1]]}",
                        "BISHOP: {mode: leap, vectors: [[2, 0], [-2, 0]]}"),
                standard.replace("ROOK: {mode: ray, vectors: [[1, 0], [0, -1], [-1, 0], [0, 1]]}",
                        "ROOK: {mode: ray, vectors: [[1, 1], [-1, -1]]}"),
                standard.replace("KING: {mode: leap, vectors: [[1, 0], [0, -1], [-1, 0], [0, 1], [1, 1], [1, -1], [-1, -1], [-1, 1]]}",
                        "KING: {mode: leap, vectors: [[1, 0], [-1, 0], [0, 1], [0, -1], [2, 0], [-2, 0]]}")
                        .replace("KNIGHT: {mode: leap, vectors: [[1, 2], [2, 1], [2, -1], [1, -2], [-1, -2], [-2, -1], [-2, 1], [-1, 2]]}",
                                "KNIGHT: {mode: leap, vectors: [[1, 0], [-1, 0]]}"),
                standard.replace("KING: {mode: leap, vectors: [[1, 0], [0, -1], [-1, 0], [0, 1], [1, 1], [1, -1], [-1, -1], [-1, 1]]}",
                        "KING: {mode: ray, vectors: [[1, 0], [-1, 0], [0, 1], [0, -1]]}"),
                standard.replace("KING: {mode: leap, vectors: [[1, 0], [0, -1], [-1, 0], [0, 1], [1, 1], [1, -1], [-1, -1], [-1, 1]]}",
                        "KING: {mode: leap, vectors: [[1, 0], [-1, 0], [0, 1], [0, -1], [2, 0], [-2, 0]]}"));
        var fens = new ArrayList<>(FENS);
        fens.addAll(List.of("4k3/8/8/8/8/8/8/4K2R w - - 0 1", "4k3/8/8/8/8/8/8/4K2n w - - 0 1",
                "4k3/8/8/8/8/8/8/4K2r w - - 0 1", "4k3/8/8/8/8/8/8/4KB2 w - - 0 1",
                "4k3/8/8/8/8/8/8/4K2b w - - 0 1", "4k3/8/8/8/8/8/8/4KB1b w - - 0 1",
                "4kb2/8/8/8/8/8/8/4K3 b - - 0 1", "4kb1B/8/8/8/8/8/8/4K3 b - - 0 1",
                "4k2N/8/8/8/8/8/8/K7 b - - 0 1"));
        for (int variant = 0; variant < variants.size(); variant++) {
            var resources = Files.createTempDirectory("legality-rules-");
            Files.writeString(resources.resolve("chess-rules.yaml"), variants.get(variant));
            URL[] urls = {resources.toUri().toURL(), ChessPosition.class.getProtectionDomain().getCodeSource().getLocation(),
                    LegalityReferencePosition.class.getProtectionDomain().getCodeSource().getLocation(),
                    Yaml.class.getProtectionDomain().getCodeSource().getLocation()};
            int accepted = 0;
            try (var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
                var actual = new ReflectedPosition(loader.loadClass("ro.atf.chess.ChessPosition"));
                var expected = new ReflectedPosition(loader.loadClass("ro.atf.chess.LegalityReferencePosition"));
                for (String fen : fens) {
                    Object a = actual.from(fen);
                    Object e = expected.from(fen);
                    require((a == null) == (e == null), "YAML variant changed FEN acceptance: " + fen);
                    if (a == null) continue;
                    reflectedTree(actual, a, expected, e, 2, fen);
                    accepted++;
                }
            }
            check.same("Accepted YAML movement variant " + variant + " retains original legality and ordered continuations", true, accepted >= 10);
        }
    }

    private static long tree(ChessPosition actual, LegalityReferencePosition expected, int depth, String path) {
        equivalent(actual, expected, path);
        if (depth == 0) return 1;
        var moves = actual.legalMoves();
        var references = expected.legalMoves();
        if (depth == 1) return moves.size();
        long leaves = 0;
        for (int i = 0; i < moves.size(); i++) {
            leaves += tree(actual.playGenerated(moves.get(i)), expected.playGenerated(references.get(i)), depth - 1,
                    path + " " + moves.get(i).uci());
        }
        return leaves;
    }

    private static void equivalent(ChessPosition actual, LegalityReferencePosition expected, String path) {
        require(actual.legalMoves().toString().equals(expected.legalMoves().toString()), "Ordered legal moves: " + path);
        require(actual.legalCapturesAndPromotions().toString().equals(expected.legalCapturesAndPromotions().toString()), "Ordered tactical moves: " + path);
        require(actual.isCheck() == expected.isCheck(), "Check: " + path);
        require(actual.hasLegalMove() == expected.hasLegalMove(), "Has legal move: " + path);
        require(actual.outcome().name().equals(expected.outcome().name()), "Outcome: " + path);
        require(actual.legalEnPassant() == expected.legalEnPassant(), "Repetition en passant: " + path);
        require(actual.searchKey().toString().equals(expected.searchKey().toString()), "Search key: " + path);
        long occupied = 0;
        for (int square = 0; square < 64; square++) {
            require(String.valueOf(actual.pieceAt(square)).equals(String.valueOf(expected.pieceAt(square))), "Board " + square + ": " + path);
            if (actual.pieceAt(square) != null) occupied |= 1L << square;
        }
        require(occupied == occupiedValue(OCCUPIED, actual), "Incremental occupancy: " + path);
    }

    private static String snapshot(ChessPosition position) {
        var result = new StringBuilder(position.searchKey().toString());
        for (int square = 0; square < 64; square++) result.append(position.pieceAt(square));
        return result.append(position.legalMoves()).append(position.isCheck()).toString();
    }

    private static void reflectedTree(ReflectedPosition actual, Object a, ReflectedPosition expected, Object e, int depth, String path) throws Exception {
        actual.verifyOccupied(a, path);
        var moves = actual.moves(a);
        var references = expected.moves(e);
        require(moves.toString().equals(references.toString()), "YAML ordered moves: " + path);
        require(actual.check.invoke(a).equals(expected.check.invoke(e)), "YAML check: " + path);
        if (depth == 0) return;
        for (int i = 0; i < moves.size(); i++) {
            reflectedTree(actual, actual.play.invoke(a, moves.get(i)), expected, expected.play.invoke(e, references.get(i)), depth - 1,
                    path + " " + moves.get(i));
        }
    }

    private static final class ReflectedPosition {
        private final Method from;
        private final Method moves;
        private final Method play;
        private final Method check;
        private final Method pieceAt;
        private final Field occupied;

        ReflectedPosition(Class<?> type) throws Exception {
            from = accessible(type.getDeclaredMethod("fromFen", String.class));
            moves = accessible(type.getDeclaredMethod("legalMoves"));
            play = accessible(type.getDeclaredMethod("playGenerated", type.getClassLoader().loadClass(type.getName() + "$Move")));
            check = accessible(type.getDeclaredMethod("isCheck"));
            pieceAt = accessible(type.getDeclaredMethod("pieceAt", int.class));
            occupied = type.getSimpleName().equals("ChessPosition") ? occupiedField(type) : null;
        }

        Object from(String fen) throws Exception {
            try { return from.invoke(null, fen); }
            catch (InvocationTargetException error) {
                if (error.getCause() instanceof IllegalArgumentException) return null;
                throw error;
            }
        }

        @SuppressWarnings("unchecked") List<Object> moves(Object position) throws Exception { return (List<Object>) moves.invoke(position); }

        void verifyOccupied(Object position, String path) throws Exception {
            if (occupied == null) return;
            long mask = 0;
            for (int square = 0; square < 64; square++) if (pieceAt.invoke(position, square) != null) mask |= 1L << square;
            require(mask == occupiedValue(occupied, position), "YAML incremental occupancy: " + path);
        }

        private static Method accessible(Method method) { method.setAccessible(true); return method; }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Field occupiedField(Class<?> type) {
        try {
            var field = type.getDeclaredField("occupied");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static long occupiedValue(Field field, Object position) {
        try { return field.getLong(position); }
        catch (IllegalAccessException error) { throw new AssertionError(error); }
    }
}
