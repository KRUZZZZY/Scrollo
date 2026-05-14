package revisionapp.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import revisionapp.core.*;
import revisionapp.model.Progress;
import revisionapp.model.Settings;
import revisionapp.model.StudyModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

public class RevisionWorkspace extends BorderPane {
    private static final int LCWC_WRITE = 0;
    private static final int LCWC_CHECK = 1;
    private static final int LCWC_COVER = 2;

    private enum View {
        HOME, FLASHCARDS, DEFINITIONS, GLOSSARY, MODULES, SETTINGS
    }

    private record LcwcItem(String id, String type, String topic, String prompt, String answer) {
    }

    private record GlossaryEntry(String topic, String kind, String heading, String body) {
    }

    private record DefinitionMatch(StudyModule.Definition definition, int start, int end) {
    }

    private record ShortcutState(
            Runnable flashFlip,
            Runnable flashNext,
            Runnable flashPrev,
            Runnable flashRestart,
            IntConsumer flashRate,
            Runnable lcwcAdvance,
            Runnable lcwcGot,
            Runnable lcwcMissed,
            Runnable lcwcSkip,
            Runnable focusDefinitionsSearch
    ) {
    }

    private final AppPaths paths;
    private final Settings settings;
    private final SettingsStore settingsStore;
    private final ModuleManager moduleManager;
    private final ProgressStore progressStore;
    private final ThemeEngine themeEngine;
    private final ExportService exportService;
    private final Set<String> selectedTopics = new LinkedHashSet<>();
    private final VBox sidebar = new VBox(10);
    private final StackPane content = new StackPane();
    private final Map<View, ToggleButton> navButtons = new EnumMap<>(View.class);
    private StudyModule module;
    private Progress progress;
    private View currentView = View.HOME;

    private Runnable flashFlip = () -> {};
    private Runnable flashNext = () -> {};
    private Runnable flashPrev = () -> {};
    private Runnable flashRestart = () -> {};
    private IntConsumer flashRate = ignored -> {};
    private Runnable lcwcAdvance = () -> {};
    private Runnable lcwcGot = () -> {};
    private Runnable lcwcMissed = () -> {};
    private Runnable lcwcSkip = () -> {};
    private Runnable focusDefinitionsSearch = () -> {};

    public RevisionWorkspace(
            AppPaths paths,
            Settings settings,
            SettingsStore settingsStore,
            ModuleManager moduleManager,
            ProgressStore progressStore,
            ThemeEngine themeEngine,
            StudyModule module,
            Progress progress
    ) {
        this.paths = paths;
        this.settings = settings;
        this.settingsStore = settingsStore;
        this.moduleManager = moduleManager;
        this.progressStore = progressStore;
        this.themeEngine = themeEngine;
        this.module = module;
        this.progress = progress;
        this.exportService = new ExportService(paths);

        getStyleClass().add("app-root");
        selectedTopics.addAll(module.categories.stream().map(category -> category.key).toList());
        setLeft(sidebar);
        setCenter(content);
        buildSidebar();
        showHome();
    }

    public void installShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.COMMA) {
                showSettings();
                event.consume();
                return;
            }
            if (event.isControlDown() && event.getCode() == KeyCode.M) {
                showModules();
                event.consume();
                return;
            }
            if (event.isControlDown() && event.getCode() == KeyCode.T) {
                cycleTheme();
                event.consume();
                return;
            }
            if (event.isControlDown() && event.getCode() == KeyCode.W) {
                showHome();
                event.consume();
                return;
            }
            if ("?".equals(event.getText())) {
                showShortcuts();
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.ESCAPE) {
                showHome();
                event.consume();
                return;
            }

            if (event.getTarget() instanceof TextInputControl && !event.isControlDown()) {
                return;
            }

            switch (currentView) {
                case FLASHCARDS -> handleFlashcardKey(event);
                case DEFINITIONS -> handleLcwcKey(event);
                case GLOSSARY -> {
                    if (event.isControlDown() && event.getCode() == KeyCode.F) {
                        focusDefinitionsSearch.run();
                        event.consume();
                    }
                }
                default -> {
                }
            }
        });
    }

    private void handleFlashcardKey(KeyEvent event) {
        if (event.getCode() == KeyCode.SPACE) {
            flashFlip.run();
            event.consume();
        } else if (event.getCode().isDigitKey()) {
            int rating = Integer.parseInt(event.getText());
            if (rating >= 1 && rating <= 5) {
                flashRate.accept(rating);
                event.consume();
            }
        } else if (event.getCode() == KeyCode.LEFT) {
            flashPrev.run();
            event.consume();
        } else if (event.getCode() == KeyCode.RIGHT) {
            flashNext.run();
            event.consume();
        } else if (event.getCode() == KeyCode.R) {
            flashRestart.run();
            event.consume();
        }
    }

    private void handleLcwcKey(KeyEvent event) {
        if (settings.lcwc.useFlashcardMode && event.getCode() == KeyCode.SPACE) {
            flashFlip.run();
            event.consume();
        } else if (settings.lcwc.useFlashcardMode && event.getCode() == KeyCode.LEFT) {
            flashPrev.run();
            event.consume();
        } else if (settings.lcwc.useFlashcardMode && event.getCode() == KeyCode.RIGHT) {
            flashNext.run();
            event.consume();
        } else if (settings.lcwc.useFlashcardMode && event.getCode().isDigitKey()) {
            int rating = Integer.parseInt(event.getText());
            if (rating >= 1 && rating <= 5) {
                flashRate.accept(rating);
                event.consume();
            }
        } else if (event.getCode() == KeyCode.ENTER) {
            lcwcAdvance.run();
            event.consume();
        } else if (event.getCode() == KeyCode.G) {
            lcwcGot.run();
            event.consume();
        } else if (event.getCode() == KeyCode.M) {
            lcwcMissed.run();
            event.consume();
        } else if (event.getCode() == KeyCode.S) {
            lcwcSkip.run();
            event.consume();
        }
    }

    private void buildSidebar() {
        sidebar.getChildren().clear();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(235);
        sidebar.setPadding(new Insets(18));
        sidebar.setFillWidth(true);

        Label title = new Label("Scrollo");
        title.getStyleClass().add("headline");
        Label moduleLabel = new Label(module.name);
        moduleLabel.getStyleClass().add("muted");
        moduleLabel.setWrapText(true);

        sidebar.getChildren().addAll(title, moduleLabel, spacer(6));
        navButtons.clear();
        addNavButton(View.HOME, "Home", this::showHome);
        addNavButton(View.FLASHCARDS, "Flashcards", () -> showFlashcards(false));
        addNavButton(View.DEFINITIONS, "Definitions", this::showDefinitions);
        addNavButton(View.GLOSSARY, "Glossary", this::showGlossary);
        sidebar.getChildren().add(spacer(8));
        addNavButton(View.MODULES, "Module Manager", this::showModules);
        addNavButton(View.SETTINGS, "Settings & Themes", this::showSettings);

        Region spring = new Region();
        VBox.setVgrow(spring, Priority.ALWAYS);
        Label footer = new Label("Data root:\n" + paths.root() + "\n\nModule:\n" + moduleManager.canonicalModulePath());
        footer.getStyleClass().add("muted");
        footer.setWrapText(true);
        sidebar.getChildren().addAll(spring, footer);
        markNav(currentView);
    }

    private void addNavButton(View view, String text, Runnable action) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAccessibleText(text);
        button.setOnAction(event -> action.run());
        navButtons.put(view, button);
        sidebar.getChildren().add(button);
    }

    private void markNav(View view) {
        navButtons.values().forEach(button -> button.setSelected(false));
        ToggleButton active = navButtons.get(view);
        if (active != null) {
            active.setSelected(true);
        }
    }

    private void setMain(View view, Node node) {
        currentView = view;
        markNav(view);
        content.getChildren().setAll(node);
    }

    private void showHome() {
        resetModeShortcuts();
        VBox page = page();
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox copy = new VBox(6);
        Label title = new Label(module.name);
        title.getStyleClass().add("headline");
        Label subtitle = new Label(module.institution + " / " + module.version);
        subtitle.getStyleClass().add("section-title");
        Node description = definitionText(module.description, "muted", TextAlignment.LEFT);
        copy.getChildren().addAll(title, subtitle, description);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Button settingsButton = button("Settings", this::showSettings);
        Button themeButton = button("Cycle Theme", this::cycleTheme);
        header.getChildren().addAll(copy, settingsButton, themeButton);

        FlowPane topics = topicFilter(() -> showHome());

        HBox stats = new HBox(12,
                metric("Due Today", String.valueOf(progressStore.dueCount(module, progress)), "SM-2 queue"),
                metric("Streak", progress.streak.current + " days", "Longest " + progress.streak.longest),
                metric("Mastered", progressStore.masteredCount(progress) + " / " + progressStore.activeCardIds(module).size(), "Rated confident")
        );
        stats.setFillHeight(true);

        GridPane modes = new GridPane();
        modes.setHgap(12);
        modes.setVgap(12);
        modes.add(modeCard("Flashcards", module.flashcards.size() + " cards",
                settings.flashcards.useLookCoverWriteCheck
                        ? "Write flashcard answers from memory, check them, then cover and retry."
                        : "Flip, rate confidence, and schedule reviews.",
                () -> showFlashcards(false)), 0, 0);
        modes.add(modeCard("Definitions", lcwcDefinitionItems(false).size() + " prompts",
                settings.lcwc.useFlashcardMode
                        ? "Flip terms and definitions, then rate confidence."
                        : "Write definitions from memory, check them, then cover and retry.",
                this::showDefinitions), 1, 0);
        modes.add(modeCard("Glossary", glossaryEntryCount() + " items", "Search definitions and flashcards.", this::showGlossary), 1, 1);
        modes.add(modeCard("Weak Spots", weakFlashcards().size() + " cards", "Focus on low-confidence or missed cards.", () -> showFlashcards(true)), 0, 2);
        modes.add(modeCard("Module Manager", "Source JSON", "Validate, edit, save, and export the canonical module.", this::showModules), 1, 2);
        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(50);
        modes.getColumnConstraints().addAll(col, col);

        HBox lower = new HBox(12);
        lower.getChildren().addAll(quickReferencePanel(), examTrapsPanel());
        HBox.setHgrow(lower.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(lower.getChildren().get(1), Priority.ALWAYS);

        page.getChildren().addAll(header, section("Topic Filter", topics), stats, modes, lower);
        setMain(View.HOME, scroll(page));
    }

    private Node metric(String title, String value, String detail) {
        VBox box = panel(4);
        box.setMinWidth(150);
        HBox.setHgrow(box, Priority.ALWAYS);
        Label t = new Label(title);
        t.getStyleClass().add("section-title");
        Label v = new Label(value);
        v.getStyleClass().add("headline");
        Label d = new Label(detail);
        d.getStyleClass().add("muted");
        box.getChildren().addAll(t, v, d);
        return box;
    }

    private Node modeCard(String title, String count, String detail, Runnable action) {
        VBox card = panel(8);
        card.setMinHeight(128);
        Label t = new Label(title);
        t.getStyleClass().add("headline");
        Label c = new Label(count);
        c.getStyleClass().add("section-title");
        Label d = new Label(detail);
        d.getStyleClass().add("muted");
        d.setWrapText(true);
        Button start = primaryButton("Start", action);
        card.getChildren().addAll(t, c, d, new Region(), start);
        VBox.setVgrow(card.getChildren().get(3), Priority.ALWAYS);
        GridPane.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node quickReferencePanel() {
        VBox box = panel(10);
        Label title = new Label("Quick Reference");
        title.getStyleClass().add("section-title");
        box.getChildren().add(title);
        for (StudyModule.KeyValue item : module.quickReference) {
            box.getChildren().add(definitionText(item.key + ": " + item.value, null, TextAlignment.LEFT));
        }
        Button export = button("Export Revision Sheet", () -> exportRevisionSheet());
        box.getChildren().add(export);
        return box;
    }

    private Node examTrapsPanel() {
        VBox box = panel(10);
        Label title = new Label("Exam Traps");
        title.getStyleClass().add("section-title");
        box.getChildren().add(title);
        for (String trap : module.examTraps) {
            box.getChildren().add(definitionText("- " + trap, null, TextAlignment.LEFT));
        }
        return box;
    }

    private void showFlashcards(boolean weakOnly) {
        showFlashcards(weakOnly, false);
    }

    private void showFlashcards(boolean weakOnly, boolean dueOnlySelected) {
        resetModeShortcuts();
        if (settings.flashcards.useLookCoverWriteCheck) {
            showFlashcardLcwc(weakOnly, dueOnlySelected);
            return;
        }
        List<StudyModule.Flashcard> cards = dueOnlySelected ? filteredFlashcards(true) : filteredFlashcards(false);
        if (weakOnly) {
            cards = cards.stream().filter(weakFlashcards()::contains).collect(Collectors.toCollection(ArrayList::new));
        }
        Collections.shuffle(cards);

        VBox page = page();
        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Button back = button("Home", this::showHome);
        Button restart = button("Restart", () -> showFlashcards(weakOnly, dueOnlySelected));
        CheckBox dueOnly = new CheckBox("Due cards only");
        dueOnly.setSelected(dueOnlySelected);
        dueOnly.setOnAction(event -> showFlashcards(weakOnly, dueOnly.isSelected()));
        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        Label title = new Label(weakOnly ? "Weak Spots" : "Flashcards");
        title.getStyleClass().add("headline");
        top.getChildren().addAll(back, title, spring, dueOnly, flashcardModeToggle(weakOnly, dueOnlySelected), restart);
        page.getChildren().add(top);
        if (cards.isEmpty()) {
            Label empty = new Label(dueOnlySelected ? "No due cards yet." : "No flashcards match the current filter.");
            empty.getStyleClass().add("headline");
            empty.setWrapText(true);
            page.getChildren().add(panelOf(empty));
        } else {
            page.getChildren().add(flashcardSession(cards, weakOnly));
        }
        setMain(View.FLASHCARDS, scroll(page));
    }

    private void showFlashcardsFromList(List<StudyModule.Flashcard> cards, boolean weakOnly) {
        showFlashcardsFromList(cards, weakOnly, false);
    }

    private void showFlashcardsFromList(List<StudyModule.Flashcard> cards, boolean weakOnly, boolean dueOnlySelected) {
        if (settings.flashcards.useLookCoverWriteCheck) {
            showFlashcardLcwcFromCards(cards, weakOnly, dueOnlySelected);
            return;
        }
        Collections.shuffle(cards);
        VBox page = page();
        HBox top = new HBox(10, button("Home", this::showHome), flashcardModeToggle(weakOnly, dueOnlySelected),
                button("Restart", () -> showFlashcards(weakOnly, dueOnlySelected)));
        top.setAlignment(Pos.CENTER_LEFT);
        page.getChildren().add(top);
        page.getChildren().add(cards.isEmpty() ? emptyState("No due cards yet.", this::showHome) : flashcardSession(cards, weakOnly));
        setMain(View.FLASHCARDS, scroll(page));
    }

    private CheckBox flashcardModeToggle(boolean weakOnly, boolean dueOnlySelected) {
        CheckBox writeCheck = new CheckBox("Write-check mode");
        writeCheck.setSelected(settings.flashcards.useLookCoverWriteCheck);
        writeCheck.setOnAction(event -> {
            settings.flashcards.useLookCoverWriteCheck = writeCheck.isSelected();
            settingsStore.save(settings);
            showFlashcards(weakOnly, dueOnlySelected);
        });
        return writeCheck;
    }

    private Node flashcardSession(List<StudyModule.Flashcard> cards, boolean weakOnly) {
        VBox shell = panel(16);
        int[] index = {0};
        boolean[] flipped = {false};

        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        Label count = new Label();
        Label topic = new Label();
        topic.getStyleClass().add("section-title");
        StackPane card = new StackPane();
        card.getStyleClass().add("card");
        card.setMinHeight(260);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPadding(new Insets(30));
        card.setOnMouseClicked(event -> {
            flipped[0] = !flipped[0];
            renderFlashcard(cards, index, flipped, bar, count, topic, card);
        });

        HBox rating = new HBox(8);
        rating.setAlignment(Pos.CENTER);
        for (int i = 1; i <= 5; i++) {
            int stars = i;
            Button star = button(stars + " star", () -> {
                StudyModule.Flashcard current = cards.get(index[0]);
                progressStore.reviewCard(progress, ProgressStore.flashcardId(current), "flashcard", stars);
                nextCard(cards, index, flipped, bar, count, topic, card);
            });
            rating.getChildren().add(star);
        }

        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER);
        Button prev = button("Back", () -> {
            index[0] = Math.max(0, index[0] - 1);
            flipped[0] = false;
            renderFlashcard(cards, index, flipped, bar, count, topic, card);
        });
        Button flip = primaryButton("Flip", () -> {
            flipped[0] = !flipped[0];
            renderFlashcard(cards, index, flipped, bar, count, topic, card);
        });
        Button next = button("Next", () -> nextCard(cards, index, flipped, bar, count, topic, card));
        controls.getChildren().addAll(prev, flip, next);

        flashFlip = flip::fire;
        flashPrev = prev::fire;
        flashNext = next::fire;
        flashRestart = () -> showFlashcards(weakOnly);
        flashRate = stars -> {
            if (flipped[0] && stars >= 1 && stars <= 5) {
                ((Button) rating.getChildren().get(stars - 1)).fire();
            }
        };

        renderFlashcard(cards, index, flipped, bar, count, topic, card);
        shell.getChildren().addAll(count, bar, topic, card, rating, controls);
        return shell;
    }

    private void renderFlashcard(List<StudyModule.Flashcard> cards, int[] index, boolean[] flipped,
                                 ProgressBar bar, Label count, Label topic, StackPane card) {
        StudyModule.Flashcard current = cards.get(index[0]);
        bar.setProgress((double) (index[0] + 1) / cards.size());
        count.setText((index[0] + 1) + " / " + cards.size());
        topic.setText(module.categoryFor(current.topic).label);
        Node face = definitionText(flipped[0] ? current.a : current.q, "headline", TextAlignment.CENTER);
        StackPane.setAlignment(face, Pos.CENTER);
        card.getChildren().setAll(face);
    }

    private void nextCard(List<StudyModule.Flashcard> cards, int[] index, boolean[] flipped,
                          ProgressBar bar, Label count, Label topic, StackPane card) {
        index[0] = Math.min(cards.size() - 1, index[0] + 1);
        flipped[0] = false;
        renderFlashcard(cards, index, flipped, bar, count, topic, card);
    }

    private void showDefinitions() {
        resetModeShortcuts();
        List<LcwcItem> items = lcwcDefinitionItems(false);
        Collections.shuffle(items);
        if (settings.lcwc.useFlashcardMode) {
            showDefinitionFlashcards(items);
            return;
        }
        showLcwcSession(items, "No definition prompts match the topic filter.", this::showHome, this::showHome,
                View.DEFINITIONS, definitionsHeader());
    }

    private Node definitionsHeader() {
        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Definitions");
        title.getStyleClass().add("headline");
        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        top.getChildren().addAll(title, spring, definitionModeToggle());
        return top;
    }

    private CheckBox definitionModeToggle() {
        CheckBox flashcardMode = new CheckBox("Flashcard mode");
        flashcardMode.setSelected(settings.lcwc.useFlashcardMode);
        flashcardMode.setOnAction(event -> {
            settings.lcwc.useFlashcardMode = flashcardMode.isSelected();
            settingsStore.save(settings);
            showDefinitions();
        });
        return flashcardMode;
    }

    private void showDefinitionFlashcards(List<LcwcItem> items) {
        resetModeShortcuts();
        if (items.isEmpty()) {
            VBox emptyPage = page();
            emptyPage.getChildren().addAll(definitionsHeader(),
                    panelOf(new Label("No definition prompts match the topic filter.")),
                    button("Home", this::showHome));
            setMain(View.DEFINITIONS, scroll(emptyPage));
            return;
        }

        VBox page = page();
        int[] index = {0};
        boolean[] flipped = {false};

        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        Label count = new Label();
        Label topic = new Label();
        topic.getStyleClass().add("section-title");
        StackPane card = new StackPane();
        card.getStyleClass().add("card");
        card.setMinHeight(260);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPadding(new Insets(30));

        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            LcwcItem current = items.get(index[0]);
            bar.setProgress((double) (index[0] + 1) / items.size());
            count.setText((index[0] + 1) + " / " + items.size());
            topic.setText(module.categoryFor(current.topic).label);
            Node face = definitionText(flipped[0] ? current.answer : current.prompt, "headline", TextAlignment.CENTER);
            StackPane.setAlignment(face, Pos.CENTER);
            card.getChildren().setAll(face);
        };
        card.setOnMouseClicked(event -> {
            flipped[0] = !flipped[0];
            render[0].run();
        });

        HBox rating = new HBox(8);
        rating.setAlignment(Pos.CENTER);
        for (int i = 1; i <= 5; i++) {
            int stars = i;
            Button star = button(stars + " star", () -> {
                LcwcItem current = items.get(index[0]);
                progressStore.reviewCard(progress, current.id, current.type, stars);
                if (index[0] < items.size() - 1) {
                    index[0]++;
                }
                flipped[0] = false;
                render[0].run();
            });
            rating.getChildren().add(star);
        }

        Button previous = button("Back", () -> {
            index[0] = Math.max(0, index[0] - 1);
            flipped[0] = false;
            render[0].run();
        });
        Button flip = primaryButton("Flip", () -> {
            flipped[0] = !flipped[0];
            render[0].run();
        });
        Button next = button("Next", () -> {
            index[0] = Math.min(items.size() - 1, index[0] + 1);
            flipped[0] = false;
            render[0].run();
        });
        HBox controls = new HBox(10, button("Home", this::showHome), previous, flip, next);
        controls.setAlignment(Pos.CENTER_RIGHT);

        flashFlip = flip::fire;
        flashPrev = previous::fire;
        flashNext = next::fire;
        flashRestart = this::showDefinitions;
        flashRate = stars -> {
            if (flipped[0] && stars >= 1 && stars <= 5) {
                ((Button) rating.getChildren().get(stars - 1)).fire();
            }
        };

        render[0].run();
        page.getChildren().addAll(definitionsHeader(), count, bar, topic, card, rating, controls);
        setMain(View.DEFINITIONS, scroll(page));
    }

    private void showFlashcardLcwc(boolean weakOnly) {
        showFlashcardLcwc(weakOnly, false);
    }

    private void showFlashcardLcwc(boolean weakOnly, boolean dueOnly) {
        resetModeShortcuts();
        List<StudyModule.Flashcard> cards = dueOnly ? filteredFlashcards(true) : filteredFlashcards(false);
        if (weakOnly) {
            cards = cards.stream().filter(weakFlashcards()::contains).collect(Collectors.toCollection(ArrayList::new));
        }
        Collections.shuffle(cards);
        showFlashcardLcwcFromCards(cards, weakOnly, dueOnly);
    }

    private void showFlashcardLcwcFromCards(List<StudyModule.Flashcard> cards, boolean weakOnly) {
        showFlashcardLcwcFromCards(cards, weakOnly, false);
    }

    private void showFlashcardLcwcFromCards(List<StudyModule.Flashcard> cards, boolean weakOnly, boolean dueOnly) {
        resetModeShortcuts();
        List<LcwcItem> items = lcwcFlashcardItems(cards);
        showLcwcSession(items, "No flashcards match the current filter.", this::showHome, this::showHome,
                View.FLASHCARDS, flashcardLcwcHeader(weakOnly, dueOnly));
    }

    private Node simpleHeader(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("headline");
        return title;
    }

    private Node flashcardLcwcHeader(boolean weakOnly, boolean dueOnlySelected) {
        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(weakOnly ? "Weak Spots" : "Flashcards");
        title.getStyleClass().add("headline");
        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        CheckBox dueOnly = new CheckBox("Due cards only");
        dueOnly.setSelected(dueOnlySelected);
        dueOnly.setOnAction(event -> showFlashcardLcwc(weakOnly, dueOnly.isSelected()));
        top.getChildren().addAll(title, spring, dueOnly, flashcardModeToggle(weakOnly, dueOnlySelected),
                button("Restart", () -> showFlashcards(weakOnly, dueOnlySelected)));
        return top;
    }

    private void showDefinitionFromText(StudyModule.Definition definition) {
        ShortcutState shortcuts = captureShortcuts();
        View returnView = currentView;
        Node returnNode = content.getChildren().isEmpty() ? null : content.getChildren().get(0);
        Runnable returnAction = () -> {
            if (returnNode == null) {
                showHome();
            } else {
                setMain(returnView, returnNode);
            }
            restoreShortcuts(shortcuts);
        };
        LcwcItem item = new LcwcItem(ProgressStore.definitionId(definition), "definition", definition.topic,
                definition.term, definition.def);
        showLcwcSession(new ArrayList<>(List.of(item)), "", returnAction, returnAction,
                View.DEFINITIONS, simpleHeader("Definition"));
    }

    private void showLcwcSession(List<LcwcItem> items, String emptyMessage, Runnable backAction, Runnable finishAction,
                                 View view, Node header) {
        resetModeShortcuts();
        if (items.isEmpty()) {
            VBox emptyPage = page();
            if (header != null) {
                emptyPage.getChildren().add(header);
            }
            Label empty = new Label(emptyMessage);
            empty.getStyleClass().add("headline");
            empty.setWrapText(true);
            emptyPage.getChildren().addAll(panelOf(empty), button("Home", backAction));
            setMain(view, scroll(emptyPage));
            return;
        }

        VBox page = page();
        int[] index = {0};
        int[] phase = {LCWC_WRITE};
        int[] renderVersion = {0};
        Timeline[] coverTimer = {null};
        TextArea answer = new TextArea();
        answer.getStyleClass().add("write-answer");
        answer.setWrapText(true);
        answer.setMinHeight(80);
        answer.setPrefHeight(95);
        answer.setMaxHeight(120);
        Runnable[] render = new Runnable[1];
        Runnable stopSession = () -> {
            renderVersion[0]++;
            stopLcwcTimer(coverTimer);
        };
        Runnable startCover = () -> {
            stopLcwcTimer(coverTimer);
            answer.clear();
            if (Math.max(0, settings.lcwc.coverTimeSec) == 0) {
                phase[0] = LCWC_WRITE;
            } else {
                phase[0] = LCWC_COVER;
            }
            render[0].run();
        };
        render[0] = () -> {
            stopLcwcTimer(coverTimer);
            renderVersion[0]++;
            int currentRender = renderVersion[0];
            page.getChildren().clear();
            if (header != null) {
                page.getChildren().add(header);
            }
            LcwcItem item = items.get(index[0]);
            Label count = new Label((index[0] + 1) + " / " + items.size());
            Label topic = new Label(module.categoryFor(item.topic).label + " / " + switch (phase[0]) {
                case LCWC_CHECK -> "Check";
                case LCWC_COVER -> "Cover";
                default -> "Write";
            });
            topic.getStyleClass().add("section-title");
            Node prompt = definitionText(item.prompt, "headline", TextAlignment.LEFT);
            Node correct;
            if (phase[0] == LCWC_CHECK) {
                correct = definitionText(item.answer, null, TextAlignment.LEFT);
            } else {
                Label covered = new Label(phase[0] == LCWC_COVER
                        ? "Covered. Wait for the timer, then write it again."
                        : "Covered. Write it from memory.");
                covered.getStyleClass().add("muted");
                correct = covered;
            }
            answer.setEditable(phase[0] == LCWC_WRITE);

            Button advance = primaryButton(switch (phase[0]) {
                case LCWC_CHECK -> "Cover";
                case LCWC_COVER -> "Write";
                default -> "Check";
            }, () -> {
                if (phase[0] == LCWC_WRITE) {
                    phase[0] = LCWC_CHECK;
                    render[0].run();
                } else if (phase[0] == LCWC_CHECK) {
                    startCover.run();
                }
            });
            if (phase[0] == LCWC_COVER) {
                startLcwcCoverCountdown(phase, currentRender, renderVersion, coverTimer, advance, render);
            }
            Button gotIt = primaryButton("Got it", () ->
                    markLcwc(items, index, phase, answer, render, finishAction, 4, renderVersion, coverTimer));
            gotIt.setDisable(phase[0] != LCWC_CHECK);
            Button skip = button("Skip", () ->
                    advanceLcwcCard(items, index, phase, answer, render, finishAction, renderVersion, coverTimer));
            Button previous = button("Back", () -> {
                index[0] = Math.max(0, index[0] - 1);
                phase[0] = LCWC_WRITE;
                answer.clear();
                render[0].run();
            });
            previous.setDisable(index[0] == 0);
            Button home = button("Home", () -> {
                stopSession.run();
                backAction.run();
            });

            lcwcAdvance = advance::fire;
            lcwcGot = gotIt::fire;
            lcwcMissed = () -> {
                if (phase[0] == LCWC_CHECK) {
                    startCover.run();
                }
            };
            lcwcSkip = skip::fire;

            Region controlSpring = new Region();
            HBox.setHgrow(controlSpring, Priority.ALWAYS);
            HBox controls = new HBox(10, home, previous, controlSpring, advance, gotIt, skip);
            controls.setAlignment(Pos.CENTER_RIGHT);
            page.getChildren().addAll(count, panelOf(topic, prompt, correct, answer, controls));
        };
        render[0].run();
        setMain(view, scroll(page));
    }

    private void startLcwcCoverCountdown(int[] phase, int currentRender, int[] renderVersion, Timeline[] coverTimer,
                                         Button advance, Runnable[] render) {
        int[] remaining = {Math.max(1, settings.lcwc.coverTimeSec)};
        advance.setDisable(true);
        advance.setText("Write (" + remaining[0] + "s)");
        Timeline[] timeline = new Timeline[1];
        timeline[0] = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (renderVersion[0] != currentRender || phase[0] != LCWC_COVER) {
                timeline[0].stop();
                return;
            }
            remaining[0]--;
            if (remaining[0] <= 0) {
                stopLcwcTimer(coverTimer);
                phase[0] = LCWC_WRITE;
                render[0].run();
            } else {
                advance.setText("Write (" + remaining[0] + "s)");
            }
        }));
        timeline[0].setCycleCount(Timeline.INDEFINITE);
        coverTimer[0] = timeline[0];
        timeline[0].play();
    }

    private void stopLcwcTimer(Timeline[] coverTimer) {
        if (coverTimer[0] != null) {
            coverTimer[0].stop();
            coverTimer[0] = null;
        }
    }

    private void advanceLcwcCard(List<LcwcItem> items, int[] index, int[] phase, TextArea answer,
                                 Runnable[] render, Runnable finishAction, int[] renderVersion,
                                 Timeline[] coverTimer) {
        if (index[0] == items.size() - 1) {
            renderVersion[0]++;
            stopLcwcTimer(coverTimer);
            finishAction.run();
            return;
        }
        index[0]++;
        phase[0] = LCWC_WRITE;
        answer.clear();
        render[0].run();
    }

    private void markLcwc(List<LcwcItem> items, int[] index, int[] phase, TextArea answer, Runnable[] render,
                          Runnable finishAction, int stars, int[] renderVersion, Timeline[] coverTimer) {
        LcwcItem item = items.get(index[0]);
        progressStore.reviewCard(progress, item.id, item.type, stars);
        if (index[0] == items.size() - 1) {
            renderVersion[0]++;
            stopLcwcTimer(coverTimer);
            finishAction.run();
            return;
        }
        index[0]++;
        phase[0] = LCWC_WRITE;
        answer.clear();
        render[0].run();
    }

    private void showGlossary() {
        resetModeShortcuts();
        VBox page = page();
        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField();
        search.setPromptText("Search glossary");
        search.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(search, Priority.ALWAYS);
        Button export = button("Export PDF", () -> exportVisibleGlossary(search.getText()));
        top.getChildren().addAll(button("Home", this::showHome), search, export);
        FlowPane topics = topicFilter(() -> showGlossary());
        VBox results = new VBox(8);

        Runnable rebuild = () -> {
            results.getChildren().clear();
            List<GlossaryEntry> visible = visibleGlossary(search.getText());
            Map<String, List<GlossaryEntry>> grouped = visible.stream()
                    .collect(Collectors.groupingBy(entry -> entry.topic, LinkedHashMap::new, Collectors.toList()));
            for (StudyModule.Category category : module.categories) {
                List<GlossaryEntry> group = grouped.get(category.key);
                if (group == null || group.isEmpty()) {
                    continue;
                }
                VBox body = new VBox(8);
                for (GlossaryEntry entry : group) {
                    TitledPane row = new TitledPane(entry.kind + ": " + entry.heading,
                            definitionText(entry.body, null, TextAlignment.LEFT));
                    row.setExpanded(false);
                    body.getChildren().add(row);
                }
                TitledPane pane = new TitledPane(category.label + " (" + group.size() + ")", body);
                pane.setExpanded(true);
                results.getChildren().add(pane);
            }
            if (results.getChildren().isEmpty()) {
                results.getChildren().add(new Label("No glossary items match your search."));
            }
        };
        search.textProperty().addListener((obs, old, value) -> rebuild.run());
        focusDefinitionsSearch = search::requestFocus;
        rebuild.run();
        page.getChildren().addAll(top, topics, results);
        setMain(View.GLOSSARY, scroll(page));
    }

    private void showModules() {
        resetModeShortcuts();
        VBox page = page();
        Label title = new Label("Module Manager");
        title.getStyleClass().add("headline");
        VBox list = new VBox(10);
        try {
            for (Path file : moduleManager.moduleFiles()) {
                StudyModule item = moduleManager.load(file);
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                VBox copy = new VBox(4);
                Label name = new Label(item.name + (item.id.equals(module.id) ? " (active)" : ""));
                name.getStyleClass().add("section-title");
                Label meta = new Label(item.institution + " / v" + item.version + " / "
                        + item.definitions.size() + " definitions / " + item.flashcards.size() + " cards");
                meta.getStyleClass().add("muted");
                copy.getChildren().addAll(name, meta);
                HBox.setHgrow(copy, Priority.ALWAYS);
                row.getChildren().addAll(copy,
                        button("Export", () -> exportModule(file)));
                list.getChildren().add(panelOf(row));
            }
        } catch (IOException ex) {
            list.getChildren().add(errorLabel(ex.getMessage()));
        }

        TextArea raw = new TextArea();
        raw.setPrefRowCount(14);
        try {
            raw.setText(Files.readString(moduleManager.canonicalModulePath()));
        } catch (IOException ex) {
            raw.setText(ex.getMessage());
        }
        Button saveRaw = primaryButton("Save Active Module JSON", () -> saveRawModule(raw.getText()));
        page.getChildren().addAll(title, list, section("Raw Active Module Editor", new VBox(8, raw, saveRaw)));
        setMain(View.MODULES, scroll(page));
    }

    private void showSettings() {
        resetModeShortcuts();
        VBox page = page();
        Label title = new Label("Settings & Themes");
        title.getStyleClass().add("headline");

        ComboBox<String> themes = new ComboBox<>();
        themes.getItems().addAll(themeIds());
        themes.setValue(settings.activeTheme);
        Button applyTheme = primaryButton("Apply Theme", () -> {
            settings.activeTheme = themes.getValue();
            saveAndApplyTheme();
        });

        ChoiceBox<String> fontSize = new ChoiceBox<>();
        fontSize.getItems().addAll("small", "medium", "large", "xlarge");
        fontSize.setValue(settings.fontSize);
        fontSize.setOnAction(event -> {
            settings.fontSize = fontSize.getValue();
            saveAndApplyTheme();
        });

        Spinner<Integer> dailyGoal = new Spinner<>(1, 200, settings.studyDailyGoal);
        dailyGoal.valueProperty().addListener((obs, old, value) -> {
            settings.studyDailyGoal = value;
            settingsStore.save(settings);
        });

        CheckBox highContrast = new CheckBox("High contrast");
        highContrast.setSelected(settings.accessibility.highContrast);
        highContrast.setOnAction(event -> {
            settings.accessibility.highContrast = highContrast.isSelected();
            saveAndApplyTheme();
        });
        CheckBox reduceMotion = new CheckBox("Reduce motion");
        reduceMotion.setSelected(settings.accessibility.reduceMotion);
        reduceMotion.setOnAction(event -> {
            settings.accessibility.reduceMotion = reduceMotion.isSelected();
            settingsStore.save(settings);
        });
        CheckBox flashcardWriteCheck = new CheckBox("Use write-check mode for flashcards");
        flashcardWriteCheck.setSelected(settings.flashcards.useLookCoverWriteCheck);
        flashcardWriteCheck.setOnAction(event -> {
            settings.flashcards.useLookCoverWriteCheck = flashcardWriteCheck.isSelected();
            settingsStore.save(settings);
        });
        CheckBox definitionFlashcards = new CheckBox("Use flashcard mode for definitions");
        definitionFlashcards.setSelected(settings.lcwc.useFlashcardMode);
        definitionFlashcards.setOnAction(event -> {
            settings.lcwc.useFlashcardMode = definitionFlashcards.isSelected();
            settingsStore.save(settings);
        });
        Spinner<Integer> coverTimer = new Spinner<>(0, 60, Math.max(0, settings.lcwc.coverTimeSec));
        coverTimer.valueProperty().addListener((obs, old, value) -> {
            settings.lcwc.coverTimeSec = value;
            settingsStore.save(settings);
        });

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.addRow(0, new Label("Theme"), new HBox(8, themes, applyTheme));
        form.addRow(1, new Label("Font size"), fontSize);
        form.addRow(2, new Label("Daily goal"), dailyGoal);
        form.addRow(3, new Label("Accessibility"), new VBox(8, highContrast, reduceMotion));
        form.addRow(4, new Label("Flashcards"), new VBox(8, flashcardWriteCheck, new HBox(8,
                new Label("Cover timer (seconds)"), coverTimer)));
        form.addRow(5, new Label("Definitions"), definitionFlashcards);

        HBox exports = new HBox(10,
                button("Export Progress ZIP", this::exportProgressZip),
                button("Export Full Backup", this::exportFullBackup),
                button("Open Data Folder", this::openDataFolder)
        );

        TextArea rawTheme = new TextArea();
        rawTheme.setPrefRowCount(16);
        loadRawTheme(rawTheme, themes.getValue());
        themes.setOnAction(event -> loadRawTheme(rawTheme, themes.getValue()));
        Button saveRawTheme = primaryButton("Save Theme JSON", () -> saveRawTheme(themes.getValue(), rawTheme.getText()));

        page.getChildren().addAll(title, section("Preferences", form), section("Backup", exports),
                section("Raw JSS Theme Editor", new VBox(8, rawTheme, saveRawTheme)));
        setMain(View.SETTINGS, scroll(page));
    }

    private void cycleTheme() {
        List<String> ids = themeIds();
        if (ids.isEmpty()) {
            return;
        }
        int current = ids.indexOf(settings.activeTheme);
        settings.activeTheme = ids.get((current + 1 + ids.size()) % ids.size());
        saveAndApplyTheme();
    }

    private void saveAndApplyTheme() {
        try {
            settingsStore.save(settings);
            themeEngine.load(settings.activeTheme);
            themeEngine.apply(getScene(), settings);
        } catch (IOException ex) {
            showError("Theme error", ex.getMessage());
        }
    }

    private List<String> themeIds() {
        try (var stream = Files.list(paths.themes())) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".jss.json"))
                    .map(path -> path.getFileName().toString().replace(".jss.json", ""))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            return List.of("dark");
        }
    }

    private List<StudyModule.Flashcard> filteredFlashcards(boolean dueOnly) {
        SpacedRepetition sr = new SpacedRepetition();
        return module.flashcards.stream()
                .filter(card -> selectedTopics.contains(card.topic))
                .filter(card -> !dueOnly || sr.isDue(progress.cards.get(ProgressStore.flashcardId(card))))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<StudyModule.Flashcard> weakFlashcards() {
        return module.flashcards.stream()
                .filter(card -> selectedTopics.contains(card.topic))
                .filter(card -> {
                    Progress.CardProgress cardProgress = progress.cards.get(ProgressStore.flashcardId(card));
                    return cardProgress != null && (!cardProgress.got || cardProgress.confidence <= 2);
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<LcwcItem> lcwcDefinitionItems(boolean dueOnly) {
        SpacedRepetition sr = new SpacedRepetition();
        List<LcwcItem> items = new ArrayList<>();
        for (StudyModule.Definition definition : module.definitions) {
            String id = ProgressStore.definitionId(definition);
            if (selectedTopics.contains(definition.topic) && (!dueOnly || sr.isDue(progress.cards.get(id)))) {
                items.add(new LcwcItem(id, "definition", definition.topic, definition.term, definition.def));
            }
        }
        return items;
    }

    private List<LcwcItem> lcwcFlashcardItems(List<StudyModule.Flashcard> flashcards) {
        List<LcwcItem> items = new ArrayList<>();
        for (StudyModule.Flashcard flashcard : flashcards) {
            String id = ProgressStore.flashcardId(flashcard);
            items.add(new LcwcItem(id, "flashcard", flashcard.topic, flashcard.q, flashcard.a));
        }
        return items;
    }

    private FlowPane topicFilter(Runnable refresh) {
        FlowPane topics = new FlowPane(8, 8);
        for (StudyModule.Category category : module.categories) {
            ToggleButton chip = new ToggleButton(category.label);
            chip.getStyleClass().add("topic-chip");
            chip.setSelected(selectedTopics.contains(category.key));
            chip.setStyle(topicStyle(category, chip.isSelected()));
            chip.setOnAction(event -> {
                boolean allTopicsSelected = selectedTopics.size() == module.categories.size();
                if (allTopicsSelected) {
                    selectedTopics.clear();
                    selectedTopics.add(category.key);
                } else if (selectedTopics.contains(category.key)) {
                    if (selectedTopics.size() == 1) {
                        selectedTopics.clear();
                        selectedTopics.addAll(module.categories.stream().map(item -> item.key).toList());
                    } else {
                        selectedTopics.remove(category.key);
                    }
                } else {
                    selectedTopics.add(category.key);
                }
                refresh.run();
            });
            topics.getChildren().add(chip);
        }
        return topics;
    }

    private String topicStyle(StudyModule.Category category, boolean selected) {
        String bg = selected ? safe(category.color.bg, "transparent") : "transparent";
        String border = safe(category.color.border, "#6366f1");
        String text = border;
        return "-fx-background-color: " + bg + "; -fx-border-color: " + border + "; -fx-text-fill: " + text + ";";
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private TextFlow definitionText(String value, String styleClass, TextAlignment alignment) {
        return definitionText(value, styleClass, alignment, true);
    }

    private TextFlow definitionText(String value, String styleClass, TextAlignment alignment, boolean allowDefinitionClick) {
        String text = value == null ? "" : value;
        TextFlow flow = new TextFlow();
        flow.setTextAlignment(alignment);
        flow.setLineSpacing(2);
        int position = 0;
        while (position < text.length()) {
            DefinitionMatch match = nextDefinitionMatch(text, position);
            if (match == null) {
                addTextSegment(flow, text.substring(position), styleClass, null, allowDefinitionClick);
                break;
            }
            if (match.start > position) {
                addTextSegment(flow, text.substring(position, match.start), styleClass, null, allowDefinitionClick);
            }
            addTextSegment(flow, text.substring(match.start, match.end), styleClass, match.definition, allowDefinitionClick);
            position = match.end;
        }
        return flow;
    }

    private void addTextSegment(TextFlow flow, String value, String styleClass, StudyModule.Definition definition,
                                boolean allowDefinitionClick) {
        if (value == null || value.isEmpty()) {
            return;
        }
        Text text = new Text(value);
        text.getStyleClass().add("rich-text");
        if (styleClass != null && !styleClass.isBlank()) {
            text.getStyleClass().add(styleClass);
        }
        if (definition != null) {
            text.getStyleClass().add("definition-link");
            Tooltip tooltip = new Tooltip(definition.term + ": " + definition.def);
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(420);
            tooltip.setShowDelay(Duration.millis(180));
            tooltip.setHideDelay(Duration.millis(80));
            Tooltip.install(text, tooltip);
            if (allowDefinitionClick) {
                text.setCursor(Cursor.HAND);
                text.setOnMousePressed(event -> event.consume());
                text.setOnMouseReleased(event -> {
                    event.consume();
                    showDefinitionFromText(definition);
                });
            }
        }
        flow.getChildren().add(text);
    }

    private DefinitionMatch nextDefinitionMatch(String text, int from) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        DefinitionMatch best = null;
        for (StudyModule.Definition definition : module.definitions.stream()
                .filter(definition -> definition.term != null && definition.def != null && definition.term.length() >= 3)
                .sorted(Comparator.comparingInt((StudyModule.Definition definition) -> definition.term.length()).reversed())
                .toList()) {
            String lowerTerm = definition.term.toLowerCase(Locale.ROOT);
            int start = lowerText.indexOf(lowerTerm, from);
            while (start >= 0) {
                int end = start + lowerTerm.length();
                if (hasTermBoundary(text, start, end)
                        && (best == null || start < best.start || start == best.start && end - start > best.end - best.start)) {
                    best = new DefinitionMatch(definition, start, end);
                    break;
                }
                start = lowerText.indexOf(lowerTerm, start + 1);
            }
        }
        return best;
    }

    private boolean hasTermBoundary(String text, int start, int end) {
        boolean left = start == 0 || !isTermCharacter(text.charAt(start - 1));
        boolean right = end == text.length() || !isTermCharacter(text.charAt(end));
        return left && right;
    }

    private boolean isTermCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '-';
    }

    private int glossaryEntryCount() {
        return module.definitions.size() + module.flashcards.size();
    }

    private List<GlossaryEntry> visibleGlossary(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<GlossaryEntry> entries = new ArrayList<>();
        for (StudyModule.Definition definition : module.definitions) {
            entries.add(new GlossaryEntry(definition.topic, "Definition", definition.term, definition.def));
        }
        for (StudyModule.Flashcard flashcard : module.flashcards) {
            entries.add(new GlossaryEntry(flashcard.topic, "Flashcard", flashcard.q, flashcard.a));
        }
        return entries.stream()
                .filter(entry -> selectedTopics.contains(entry.topic))
                .filter(entry -> q.isBlank()
                        || entry.kind.toLowerCase(Locale.ROOT).contains(q)
                        || entry.heading.toLowerCase(Locale.ROOT).contains(q)
                        || entry.body.toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    private void switchModule(String id) {
        try {
            module = moduleManager.loadActive(id);
            progress = progressStore.load(module);
            settings.activeModule = module.id;
            settingsStore.save(settings);
            selectedTopics.clear();
            selectedTopics.addAll(module.categories.stream().map(category -> category.key).toList());
            buildSidebar();
            showHome();
        } catch (IOException ex) {
            showError("Module error", ex.getMessage());
        }
    }

    private void exportModule(Path file) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Module");
        chooser.setInitialFileName(file.getFileName().toString());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        java.io.File target = chooser.showSaveDialog(getScene().getWindow());
        if (target == null) {
            return;
        }
        try {
            Files.copy(file, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            showError("Export failed", ex.getMessage());
        }
    }

    private void saveRawModule(String json) {
        try {
            StudyModule edited = JsonSupport.MAPPER.readValue(json, StudyModule.class);
            moduleManager.save(edited);
            switchModule(edited.id);
        } catch (Exception ex) {
            showError("Module JSON invalid", ex.getMessage());
        }
    }

    private void loadRawTheme(TextArea rawTheme, String themeId) {
        try {
            rawTheme.setText(Files.readString(paths.themes().resolve(themeId + ".jss.json")));
        } catch (IOException ex) {
            rawTheme.setText(ex.getMessage());
        }
    }

    private void saveRawTheme(String themeId, String json) {
        try {
            JsonSupport.MAPPER.readTree(json);
            Files.writeString(paths.themes().resolve(themeId + ".jss.json"), json);
            if (themeId.equals(settings.activeTheme)) {
                saveAndApplyTheme();
            }
        } catch (Exception ex) {
            showError("Theme JSON invalid", ex.getMessage());
        }
    }

    private void exportVisibleGlossary(String query) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Glossary PDF");
        chooser.setInitialFileName(module.id + "-glossary.pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        java.io.File target = chooser.showSaveDialog(getScene().getWindow());
        if (target == null) {
            return;
        }
        List<StudyModule.Definition> pseudo = visibleGlossary(query).stream().map(entry -> {
            StudyModule.Definition definition = new StudyModule.Definition();
            definition.topic = entry.topic;
            definition.term = entry.kind + ": " + entry.heading;
            definition.def = entry.body;
            return definition;
        }).collect(Collectors.toCollection(ArrayList::new));
        try {
            exportService.exportGlossaryPdf(target.toPath(), module, pseudo);
        } catch (IOException ex) {
            showError("PDF export failed", ex.getMessage());
        }
    }

    private void exportRevisionSheet() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Revision Sheet PDF");
        chooser.setInitialFileName(module.id + "-revision-sheet.pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        java.io.File target = chooser.showSaveDialog(getScene().getWindow());
        if (target == null) {
            return;
        }
        List<StudyModule.Definition> pseudo = module.quickReference.stream().map(item -> {
            StudyModule.Definition definition = new StudyModule.Definition();
            definition.topic = module.categories.isEmpty() ? "misc" : module.categories.get(0).key;
            definition.term = item.key;
            definition.def = item.value;
            return definition;
        }).collect(Collectors.toCollection(ArrayList::new));
        for (String trap : module.examTraps) {
            StudyModule.Definition definition = new StudyModule.Definition();
            definition.topic = module.categories.isEmpty() ? "misc" : module.categories.get(0).key;
            definition.term = "Trap";
            definition.def = trap;
            pseudo.add(definition);
        }
        try {
            exportService.exportDefinitionsPdf(target.toPath(), module, pseudo);
        } catch (IOException ex) {
            showError("PDF export failed", ex.getMessage());
        }
    }

    private void exportProgressZip() {
        exportZip("Export Progress", module.id + "-progress.zip", true);
    }

    private void exportFullBackup() {
        exportZip("Export Full Backup", "scrollo-backup.zip", false);
    }

    private void exportZip(String title, String filename, boolean progressOnly) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(filename);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP", "*.zip"));
        java.io.File target = chooser.showSaveDialog(getScene().getWindow());
        if (target == null) {
            return;
        }
        try {
            if (progressOnly) {
                exportService.exportProgressZip(target.toPath());
            } else {
                exportService.exportFullBackup(target.toPath());
            }
        } catch (IOException ex) {
            showError("Export failed", ex.getMessage());
        }
    }

    private void openDataFolder() {
        try {
            java.awt.Desktop.getDesktop().open(paths.root().toFile());
        } catch (IOException ex) {
            showError("Open folder failed", ex.getMessage());
        }
    }

    private void showShortcuts() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Keyboard Shortcuts");
        alert.setHeaderText("Scrollo shortcuts");
        alert.setContentText("""
                Global: Ctrl+, settings / Ctrl+M modules / Ctrl+T cycle theme / Ctrl+W home / ? shortcuts / Esc home
                Flashcards: Space flip / 1-5 rate / Left-Right move / R restart
                Definitions: Enter advance / G got it / M cover / S skip / Space flip in flashcard mode
                Glossary: Ctrl+F focus search
                """);
        alert.showAndWait();
    }

    private ShortcutState captureShortcuts() {
        return new ShortcutState(flashFlip, flashNext, flashPrev, flashRestart, flashRate,
                lcwcAdvance, lcwcGot, lcwcMissed, lcwcSkip, focusDefinitionsSearch);
    }

    private void restoreShortcuts(ShortcutState state) {
        flashFlip = state.flashFlip;
        flashNext = state.flashNext;
        flashPrev = state.flashPrev;
        flashRestart = state.flashRestart;
        flashRate = state.flashRate;
        lcwcAdvance = state.lcwcAdvance;
        lcwcGot = state.lcwcGot;
        lcwcMissed = state.lcwcMissed;
        lcwcSkip = state.lcwcSkip;
        focusDefinitionsSearch = state.focusDefinitionsSearch;
    }

    private void resetModeShortcuts() {
        flashFlip = () -> {};
        flashNext = () -> {};
        flashPrev = () -> {};
        flashRestart = () -> {};
        flashRate = ignored -> {};
        lcwcAdvance = () -> {};
        lcwcGot = () -> {};
        lcwcMissed = () -> {};
        lcwcSkip = () -> {};
        focusDefinitionsSearch = () -> {};
    }

    private VBox page() {
        VBox page = new VBox(16);
        page.setPadding(new Insets(22));
        page.setFillWidth(true);
        return page;
    }

    private ScrollPane scroll(Node node) {
        ScrollPane scroll = new ScrollPane(node);
        scroll.getStyleClass().add("content-scroll");
        scroll.setFitToWidth(true);
        return scroll;
    }

    private VBox panel(int spacing) {
        VBox panel = new VBox(spacing);
        panel.getStyleClass().add("panel");
        panel.setPadding(new Insets(16));
        return panel;
    }

    private VBox panelOf(Node... nodes) {
        VBox panel = panel(10);
        panel.getChildren().addAll(nodes);
        return panel;
    }

    private Node section(String title, Node content) {
        VBox section = new VBox(8);
        Label label = new Label(title);
        label.getStyleClass().add("section-title");
        section.getChildren().addAll(label, content);
        return section;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(event -> action.run());
        button.setAccessibleText(text);
        return button;
    }

    private Button primaryButton(String text, Runnable action) {
        Button button = button(text, action);
        button.getStyleClass().add("primary");
        return button;
    }

    private Region spacer(double height) {
        Region spacer = new Region();
        spacer.setMinHeight(height);
        spacer.setPrefHeight(height);
        return spacer;
    }

    private Node emptyState(String message, Runnable back) {
        VBox box = page();
        Label label = new Label(message);
        label.getStyleClass().add("headline");
        label.setWrapText(true);
        box.getChildren().addAll(label, button("Home", back));
        return box;
    }

    private Label errorLabel(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("wrong");
        label.setWrapText(true);
        return label;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
