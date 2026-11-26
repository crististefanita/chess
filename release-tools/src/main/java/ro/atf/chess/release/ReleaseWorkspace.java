package ro.atf.chess.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Locates the owning checkout from a module, guide or working directory. */
public final class ReleaseWorkspace {
    public static final String MODULE = "release-tools";
    public static final String SIGNING = MODULE + "/.atf-signing";
    public static final String SCRIPTS = MODULE + "/scripts";

    public record Layout(Path root, Path module, Path scripts, Path signing) { }

    private ReleaseWorkspace() { }

    public static Layout locate(Path start) throws IOException {
        Path directory = start.toRealPath();
        if (Files.isRegularFile(directory)) directory = directory.getParent();
        for (Path root = directory; root != null; root = root.getParent()) {
            if (Files.isRegularFile(root.resolve("pom.xml")) && Files.isRegularFile(root.resolve(MODULE + "/pom.xml"))
                    && Files.isDirectory(root.resolve("chess-core")) && Files.isRegularFile(root.resolve("release/index.json"))) {
                return new Layout(root, root.resolve(MODULE), root.resolve(SCRIPTS), root.resolve(SIGNING));
            }
        }
        throw new IOException("No Chess release workspace above " + start);
    }
}
