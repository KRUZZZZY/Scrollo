package revisionapp.core;

import revisionapp.model.WindowState;

import java.io.IOException;
import java.nio.file.Files;

public class WindowStateStore {
    private final AppPaths paths;

    public WindowStateStore(AppPaths paths) {
        this.paths = paths;
    }

    public WindowState load() {
        if (!Files.exists(paths.window())) {
            return new WindowState();
        }
        try {
            return JsonSupport.read(paths.window(), WindowState.class);
        } catch (IOException ex) {
            return new WindowState();
        }
    }

    public void save(WindowState state) {
        try {
            JsonSupport.write(paths.window(), state);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save window state", ex);
        }
    }
}
