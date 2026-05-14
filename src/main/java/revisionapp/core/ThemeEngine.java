package revisionapp.core;

import javafx.scene.Scene;
import revisionapp.model.Settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ThemeEngine {
    private final AppPaths paths;
    private Map<String, Object> theme = new LinkedHashMap<>();
    private Map<String, Object> tokens = new LinkedHashMap<>();
    private String currentStylesheet;

    public ThemeEngine(AppPaths paths) {
        this.paths = paths;
    }

    public void load(String themeId) throws IOException {
        Path themePath = paths.themes().resolve(themeId + ".jss.json");
        if (!Files.exists(themePath)) {
            themePath = paths.themes().resolve("dark.jss.json");
        }
        theme = JsonSupport.readMap(themePath);
        tokens = flattenTokens(theme);
    }

    public String getToken(String key) {
        Object value = tokens.get(key);
        return value == null ? "" : String.valueOf(resolve(value, Set.of()));
    }

    public void apply(Scene scene, Settings settings) throws IOException {
        if (theme.isEmpty()) {
            load(settings.activeTheme);
        }
        if (currentStylesheet != null) {
            scene.getStylesheets().remove(currentStylesheet);
        }
        String css = generateCss(settings);
        Path cssPath = paths.root().resolve("generated-theme.css");
        Files.writeString(cssPath, css, StandardCharsets.UTF_8);
        currentStylesheet = cssPath.toUri().toString();
        scene.getStylesheets().add(currentStylesheet);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenTokens(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String section : new String[]{"colors", "typography", "shape", "spacing"}) {
            Object value = source.get(section);
            if (value instanceof Map<?, ?> sectionMap) {
                sectionMap.forEach((key, token) -> result.put(String.valueOf(key), token));
            }
        }
        Object components = source.get("components");
        if (components instanceof Map<?, ?> componentMap) {
            componentMap.forEach((name, value) -> {
                if (value instanceof Map<?, ?> fields) {
                    fields.forEach((field, token) -> result.put(name + "." + field, token));
                }
            });
        }
        return result;
    }

    private Object resolve(Object value, Set<String> seen) {
        if (!(value instanceof String text) || !text.startsWith("@")) {
            return value;
        }
        String key = text.substring(1);
        if (seen.contains(key)) {
            throw new IllegalStateException("Circular theme reference at " + key);
        }
        Object target = tokens.get(key);
        if (target == null) {
            return value;
        }
        Set<String> nextSeen = new java.util.LinkedHashSet<>(seen);
        nextSeen.add(key);
        return resolve(target, nextSeen);
    }

    private String generateCss(Settings settings) {
        String background = cssColor("background", "#030712", settings);
        String surface = cssColor("surface", "#0a0f1e", settings);
        String raised = cssColor("surface-raised", "#111827", settings);
        String border = cssColor("border", "#1e293b", settings);
        String focus = cssColor("border-focus", "#6366f1", settings);
        String primary = cssColor("text-primary", "#e0e7ff", settings);
        String secondary = cssColor("text-secondary", "#9ca3af", settings);
        String muted = cssColor("text-muted", "#4b5563", settings);
        String accent = cssColor("accent", "#6366f1", settings);
        String accentHover = cssColor("accent-hover", "#4f46e5", settings);
        String accentSubtle = cssColor("accent-subtle", "rgba(99,102,241,0.15)", settings);
        String success = cssColor("success", "#22c55e", settings);
        String successSubtle = cssColor("success-subtle", "rgba(34,197,94,0.12)", settings);
        String danger = cssColor("danger", "#ef4444", settings);
        String dangerSubtle = cssColor("danger-subtle", "rgba(239,68,68,0.12)", settings);
        String themeId = settings.activeTheme == null ? "" : settings.activeTheme.toLowerCase();
        boolean darkChrome = themeId.equals("dark") || themeId.equals("grey");
        String navBg = darkChrome ? "#e5e7eb" : raised;
        String navSelected = darkChrome ? "#d7dde7" : accentSubtle;
        String navText = darkChrome ? "#111827" : primary;
        String navBorder = darkChrome ? "#c3ccd9" : border;
        String navHoverBorder = darkChrome ? "#9ca8ba" : border;
        int base = settings.baseFontPx();

        return """
                .root {
                  -rev-background: %s;
                  -rev-surface: %s;
                  -rev-raised: %s;
                  -rev-border: %s;
                  -rev-focus: %s;
                  -rev-text: %s;
                  -rev-secondary: %s;
                  -rev-muted: %s;
                  -rev-accent: %s;
                  -rev-accent-hover: %s;
                  -rev-accent-subtle: %s;
                  -rev-success: %s;
                  -rev-success-subtle: %s;
                  -rev-danger: %s;
                  -rev-danger-subtle: %s;
                  -rev-nav-bg: %s;
                  -rev-nav-selected: %s;
                  -rev-nav-text: %s;
                  -rev-nav-border: %s;
                  -rev-nav-hover-border: %s;
                  -fx-background-color: -rev-background;
                  -fx-font-family: "Crimson Pro", "Georgia", serif;
                  -fx-font-size: %dpx;
                  -fx-text-fill: -rev-text;
                }
                .app-root, .content-scroll, .viewport {
                  -fx-background-color: -rev-background;
                }
                .sidebar {
                  -fx-background-color: -rev-surface;
                  -fx-border-color: transparent -rev-border transparent transparent;
                  -fx-border-width: 0 1 0 0;
                }
                .panel, .card {
                  -fx-background-color: -rev-surface;
                  -fx-background-radius: 8;
                  -fx-border-color: -rev-border;
                  -fx-border-radius: 8;
                  -fx-border-width: 1;
                }
                .panel-raised {
                  -fx-background-color: -rev-raised;
                  -fx-background-radius: 8;
                  -fx-border-color: -rev-border;
                  -fx-border-radius: 8;
                }
                .headline {
                  -fx-font-size: %dpx;
                  -fx-font-weight: 700;
                  -fx-text-fill: -rev-text;
                  -fx-fill: -rev-text;
                }
                .section-title {
                  -fx-font-family: "Space Mono", "Courier New", monospace;
                  -fx-font-size: %dpx;
                  -fx-text-fill: -rev-secondary;
                  -fx-fill: -rev-secondary;
                }
                .muted {
                  -fx-text-fill: -rev-muted;
                  -fx-fill: -rev-muted;
                }
                .label, .text {
                  -fx-text-fill: -rev-text;
                  -fx-fill: -rev-text;
                }
                .rich-text {
                  -fx-fill: -rev-text;
                }
                .definition-link {
                  -fx-fill: -rev-accent;
                  -fx-underline: true;
                }
                .definition-link:hover {
                  -fx-fill: -rev-accent-hover;
                }
                .quiz-option-text {
                  -fx-font-size: %dpx;
                  -fx-fill: -rev-text;
                }
                .quiz-option-text.definition-link {
                  -fx-fill: -rev-accent;
                  -fx-underline: true;
                }
                .button, .toggle-button {
                  -fx-background-color: -rev-raised;
                  -fx-border-color: -rev-border;
                  -fx-border-radius: 7;
                  -fx-background-radius: 7;
                  -fx-text-fill: -rev-text;
                  -fx-padding: 7 12 7 12;
                }
                .button:hover, .toggle-button:hover {
                  -fx-border-color: -rev-focus;
                  -fx-text-fill: -rev-text;
                }
                .button.quiz-option {
                  -fx-padding: 4 8 4 8;
                  -fx-alignment: center-left;
                }
                .check-box {
                  -fx-text-fill: -rev-text;
                }
                .check-box .box {
                  -fx-background-color: -rev-raised;
                  -fx-border-color: -rev-border;
                  -fx-border-radius: 4;
                  -fx-background-radius: 4;
                }
                .button.primary {
                  -fx-background-color: -rev-accent;
                  -fx-border-color: -rev-accent;
                  -fx-text-fill: white;
                }
                .button.primary:hover {
                  -fx-background-color: -rev-accent-hover;
                }
                .nav-button {
                  -fx-alignment: center-left;
                  -fx-max-width: infinity;
                  -fx-background-color: -rev-nav-bg;
                  -fx-border-color: -rev-nav-border;
                  -fx-text-fill: -rev-nav-text;
                }
                .nav-button:hover, .nav-button:selected {
                  -fx-background-color: -rev-nav-selected;
                  -fx-border-color: -rev-nav-hover-border;
                  -fx-text-fill: -rev-nav-text;
                }
                .nav-button .text, .nav-button:hover .text, .nav-button:selected .text {
                  -fx-fill: -rev-nav-text;
                }
                .topic-chip {
                  -fx-background-color: -rev-raised;
                  -fx-border-color: -rev-border;
                  -fx-text-fill: -rev-text;
                }
                .topic-chip:selected {
                  -fx-background-color: -rev-accent-subtle;
                }
                .text-field, .text-area, .combo-box-base, .choice-box {
                  -fx-background-color: -rev-raised;
                  -fx-border-color: -rev-border;
                  -fx-border-radius: 7;
                  -fx-background-radius: 7;
                  -fx-text-fill: -rev-text;
                  -fx-prompt-text-fill: -rev-muted;
                }
                .text-input:focused {
                  -fx-border-color: -rev-focus;
                }
                .text-input:disabled {
                  -fx-opacity: 1;
                }
                .text-area .content {
                  -fx-background-color: -rev-raised;
                }
                .text-area, .text-area .text, .write-answer, .write-answer .text {
                  -fx-text-fill: -rev-text;
                  -fx-fill: -rev-text;
                }
                .progress-bar > .track {
                  -fx-background-color: -rev-raised;
                  -fx-background-radius: 99;
                }
                .progress-bar > .bar {
                  -fx-background-color: -rev-accent;
                  -fx-background-radius: 99;
                }
                .correct {
                  -fx-background-color: -rev-success-subtle;
                  -fx-border-color: -rev-success;
                }
                .wrong {
                  -fx-background-color: -rev-danger-subtle;
                  -fx-border-color: -rev-danger;
                }
                .list-cell, .table-view, .tree-view {
                  -fx-background-color: -rev-surface;
                  -fx-text-fill: -rev-text;
                }
                .titled-pane > .title {
                  -fx-background-color: -rev-raised;
                  -fx-border-color: -rev-border;
                }
                .titled-pane > *.content {
                  -fx-background-color: -rev-surface;
                  -fx-border-color: -rev-border;
                }
                """.formatted(background, surface, raised, border, focus, primary, secondary, muted, accent, accentHover,
                accentSubtle, success, successSubtle, danger, dangerSubtle, navBg, navSelected, navText, navBorder,
                navHoverBorder, base, base + 13, Math.max(10, base - 2), 11);
    }

    private String cssColor(String key, String fallback, Settings settings) {
        String color = Objects.toString(resolve(tokens.getOrDefault(key, fallback), Set.of()), fallback);
        if (!settings.accessibility.highContrast) {
            return color;
        }
        return switch (key) {
            case "background" -> "#000000";
            case "surface", "surface-raised" -> "#0b0b0b";
            case "border", "border-focus" -> "#ffffff";
            case "text-primary", "text-secondary" -> "#ffffff";
            case "text-muted" -> "#d4d4d4";
            default -> color;
        };
    }
}
