package revisionapp.core;

import revisionapp.model.Settings;

import java.io.IOException;
import java.nio.file.Files;

public class SettingsStore {
    private final AppPaths paths;

    public SettingsStore(AppPaths paths) {
        this.paths = paths;
    }

    public Settings load() {
        if (!Files.exists(paths.settings())) {
            return new Settings();
        }
        try {
            return JsonSupport.read(paths.settings(), Settings.class);
        } catch (IOException ex) {
            return new Settings();
        }
    }

    public void save(Settings settings) {
        try {
            JsonSupport.write(paths.settings(), settings);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save settings", ex);
        }
    }
}
