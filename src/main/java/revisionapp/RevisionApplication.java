package revisionapp;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import revisionapp.core.AppPaths;
import revisionapp.core.DataBootstrap;
import revisionapp.core.ModuleManager;
import revisionapp.core.ProgressStore;
import revisionapp.core.SettingsStore;
import revisionapp.core.ThemeEngine;
import revisionapp.core.WindowStateStore;
import revisionapp.model.Progress;
import revisionapp.model.Settings;
import revisionapp.model.StudyModule;
import revisionapp.model.WindowState;
import revisionapp.ui.RevisionWorkspace;

public class RevisionApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        AppPaths paths = AppPaths.defaults();
        new DataBootstrap(paths).ensureDataRoot();

        SettingsStore settingsStore = new SettingsStore(paths);
        Settings settings = settingsStore.load();
        ModuleManager moduleManager = new ModuleManager(paths);
        StudyModule module = moduleManager.loadActive(settings.activeModule);
        settings.activeModule = module.id;

        ProgressStore progressStore = new ProgressStore(paths);
        Progress progress = progressStore.load(module);
        ThemeEngine themeEngine = new ThemeEngine(paths);
        themeEngine.load(settings.activeTheme);

        RevisionWorkspace workspace = new RevisionWorkspace(
                paths,
                settings,
                settingsStore,
                moduleManager,
                progressStore,
                themeEngine,
                module,
                progress
        );

        WindowStateStore windowStore = new WindowStateStore(paths);
        WindowState window = windowStore.load();
        Scene scene = new Scene(workspace, window.width, window.height);
        themeEngine.apply(scene, settings);

        stage.setTitle("Scrollo");
        stage.setMinWidth(700);
        stage.setMinHeight(520);
        restoreWindow(stage, window);
        stage.setScene(scene);
        workspace.installShortcuts(scene);
        stage.show();

        PauseTransition debounce = new PauseTransition(Duration.millis(500));
        Runnable scheduleSave = () -> {
            debounce.stop();
            debounce.setOnFinished(event -> windowStore.save(captureWindow(stage)));
            debounce.playFromStart();
        };
        stage.xProperty().addListener((obs, old, value) -> scheduleSave.run());
        stage.yProperty().addListener((obs, old, value) -> scheduleSave.run());
        stage.widthProperty().addListener((obs, old, value) -> scheduleSave.run());
        stage.heightProperty().addListener((obs, old, value) -> scheduleSave.run());
        stage.maximizedProperty().addListener((obs, old, value) -> scheduleSave.run());
        stage.setOnCloseRequest(event -> {
            settingsStore.save(settings);
            windowStore.save(captureWindow(stage));
        });
    }

    private void restoreWindow(Stage stage, WindowState state) {
        Rectangle2D saved = new Rectangle2D(state.x, state.y, state.width, state.height);
        boolean visible = Screen.getScreensForRectangle(saved).stream()
                .map(Screen::getVisualBounds)
                .anyMatch(bounds -> bounds.intersects(saved));
        if (visible) {
            stage.setX(state.x);
            stage.setY(state.y);
        } else {
            Rectangle2D primary = Screen.getPrimary().getVisualBounds();
            stage.setX(primary.getMinX() + (primary.getWidth() - state.width) / 2);
            stage.setY(primary.getMinY() + (primary.getHeight() - state.height) / 2);
        }
        stage.setWidth(Math.max(700, state.width));
        stage.setHeight(Math.max(520, state.height));
        stage.setMaximized(state.maximised);
    }

    private WindowState captureWindow(Stage stage) {
        WindowState state = new WindowState();
        state.x = stage.getX();
        state.y = stage.getY();
        state.width = stage.getWidth();
        state.height = stage.getHeight();
        state.maximised = stage.isMaximized();
        return state;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
