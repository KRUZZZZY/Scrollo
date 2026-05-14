package revisionapp.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DataBootstrap {
    private final AppPaths paths;

    public DataBootstrap(AppPaths paths) {
        this.paths = paths;
    }

    public void ensureDataRoot() throws IOException {
        Files.createDirectories(paths.themes());
        Files.createDirectories(paths.progress());

        copyIfMissing("/modules/cs375-logic.json", paths.canonicalModule());
        copyIfMissing("/themes/dark.jss.json", paths.themes().resolve("dark.jss.json"));
        copyIfMissing("/themes/grey.jss.json", paths.themes().resolve("grey.jss.json"));
        copyIfMissing("/themes/light.jss.json", paths.themes().resolve("light.jss.json"));
    }

    private void copyIfMissing(String resource, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = DataBootstrap.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing bundled resource " + resource);
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
        }
    }
}
