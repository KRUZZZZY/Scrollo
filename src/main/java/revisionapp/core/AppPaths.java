package revisionapp.core;

import java.nio.file.Files;
import java.nio.file.Path;

public record AppPaths(Path root) {
    private static final String MODULE_ID = "cs375-logic";

    public static AppPaths defaults() {
        return new AppPaths(Path.of(System.getProperty("user.home"), ".revisionapp"));
    }

    public Path settings() {
        return root.resolve("settings.json");
    }

    public Path window() {
        return root.resolve("window.json");
    }

    public Path modules() {
        return locateProjectModules();
    }

    public Path canonicalModule() {
        return modules().resolve(MODULE_ID + ".json");
    }

    public Path themes() {
        return root.resolve("themes");
    }

    public Path progress() {
        return root.resolve("progress");
    }

    public Path progressFor(String moduleId) {
        return progress().resolve(moduleId + ".json");
    }

    private static Path locateProjectModules() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path path = current; path != null; path = path.getParent()) {
            Path candidate = path.resolve("src/main/resources/modules");
            if (Files.exists(candidate.resolve(MODULE_ID + ".json"))) {
                return candidate;
            }
        }
        return current.resolve("src/main/resources/modules");
    }
}
