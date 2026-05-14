package revisionapp.core;

import revisionapp.model.StudyModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public class ModuleManager {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9-]+");
    private static final String CANONICAL_MODULE_ID = "cs375-logic";
    private final AppPaths paths;

    public ModuleManager(AppPaths paths) {
        this.paths = paths;
    }

    public List<Path> moduleFiles() throws IOException {
        Path module = canonicalModulePath();
        if (!Files.exists(module)) {
            throw new IOException("Missing canonical module " + module);
        }
        return List.of(module);
    }

    public List<StudyModule> loadAll() throws IOException {
        return moduleFiles().stream().map(path -> {
            try {
                return load(path);
            } catch (IOException ex) {
                StudyModule module = new StudyModule();
                module.id = path.getFileName().toString();
                module.name = "Invalid module: " + ex.getMessage();
                return module;
            }
        }).toList();
    }

    public StudyModule loadActive(String moduleId) throws IOException {
        return load(canonicalModulePath());
    }

    public StudyModule load(Path path) throws IOException {
        StudyModule module = JsonSupport.read(path, StudyModule.class);
        validate(module);
        return module;
    }

    public void save(StudyModule module) throws IOException {
        validate(module);
        JsonSupport.write(canonicalModulePath(), module);
    }

    public Path canonicalModulePath() {
        return paths.canonicalModule();
    }

    public void validate(StudyModule module) throws IOException {
        if (module.id == null || !ID_PATTERN.matcher(module.id).matches()) {
            throw new IOException("Module id must match [a-z0-9-]+");
        }
        if (!CANONICAL_MODULE_ID.equals(module.id)) {
            throw new IOException("Module id must remain " + CANONICAL_MODULE_ID);
        }
        if (module.version == null || module.version.isBlank()) {
            throw new IOException("Module version is required");
        }
        List<String> categoryKeys = module.categories.stream().map(category -> category.key).toList();
        long distinct = categoryKeys.stream().distinct().count();
        if (distinct != categoryKeys.size()) {
            throw new IOException("Category keys must be unique");
        }
        for (StudyModule.Definition definition : module.definitions) {
            requireTopic(categoryKeys, definition.topic, "definition " + definition.term);
        }
        for (StudyModule.Flashcard flashcard : module.flashcards) {
            requireTopic(categoryKeys, flashcard.topic, "flashcard " + flashcard.q);
        }
        for (StudyModule.QuizQuestion question : module.quiz) {
            requireTopic(categoryKeys, question.topic, "quiz question");
            if (question.options.size() < 2 || question.options.size() > 6) {
                throw new IOException("Quiz questions must have 2-6 options");
            }
            if (question.answer < 0 || question.answer >= question.options.size()) {
                throw new IOException("Quiz answer index is out of range");
            }
        }
    }

    private void requireTopic(List<String> categoryKeys, String topic, String label) throws IOException {
        if (!categoryKeys.contains(topic)) {
            throw new IOException(label + " references unknown topic " + topic);
        }
    }
}
