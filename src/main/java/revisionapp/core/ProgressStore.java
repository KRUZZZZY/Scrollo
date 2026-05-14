package revisionapp.core;

import revisionapp.model.Progress;
import revisionapp.model.StudyModule;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;

public class ProgressStore {
    private final AppPaths paths;
    private final SpacedRepetition spacedRepetition = new SpacedRepetition();

    public ProgressStore(AppPaths paths) {
        this.paths = paths;
    }

    public Progress load(StudyModule module) {
        Progress progress;
        if (Files.exists(paths.progressFor(module.id))) {
            try {
                progress = JsonSupport.read(paths.progressFor(module.id), Progress.class);
            } catch (IOException ex) {
                progress = new Progress();
            }
        } else {
            progress = new Progress();
        }

        progress.moduleId = module.id;
        progress.moduleVersion = module.version;
        if (progress.cards == null) {
            progress.cards = new java.util.LinkedHashMap<>();
        }
        if (progress.quizHistory == null) {
            progress.quizHistory = new java.util.ArrayList<>();
        }
        if (progress.streak == null) {
            progress.streak = new Progress.Streak();
        }
        if (progress.archived == null) {
            progress.archived = new java.util.LinkedHashMap<>();
        }
        migrate(module, progress);
        save(progress);
        return progress;
    }

    public void save(Progress progress) {
        try {
            JsonSupport.write(paths.progressFor(progress.moduleId), progress);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save progress", ex);
        }
    }

    public Progress.CardProgress reviewCard(Progress progress, String cardId, String type, int stars) {
        Progress.CardProgress next = spacedRepetition.review(progress.cards.get(cardId), type, stars);
        progress.cards.put(cardId, next);
        touchStudy(progress);
        save(progress);
        return next;
    }

    public void addQuizAttempt(Progress progress, Progress.QuizAttempt attempt) {
        progress.quizHistory.add(0, attempt);
        while (progress.quizHistory.size() > 30) {
            progress.quizHistory.remove(progress.quizHistory.size() - 1);
        }
        touchStudy(progress);
        save(progress);
    }

    public int dueCount(StudyModule module, Progress progress) {
        SpacedRepetition sr = new SpacedRepetition();
        int count = 0;
        for (String id : activeCardIds(module)) {
            if (sr.isDue(progress.cards.get(id))) {
                count++;
            }
        }
        return count;
    }

    public int masteredCount(Progress progress) {
        return (int) progress.cards.values().stream()
                .filter(card -> card.got && card.confidence >= 4)
                .count();
    }

    private void migrate(StudyModule module, Progress progress) {
        Set<String> active = activeCardIds(module);
        Set<String> current = new LinkedHashSet<>(progress.cards.keySet());
        for (String cardId : current) {
            if (!active.contains(cardId)) {
                progress.archived.put(cardId, progress.cards.remove(cardId));
            }
        }
    }

    public Set<String> activeCardIds(StudyModule module) {
        Set<String> ids = new LinkedHashSet<>();
        for (StudyModule.Definition definition : module.definitions) {
            ids.add(definitionId(definition));
        }
        for (StudyModule.Flashcard flashcard : module.flashcards) {
            ids.add(flashcardId(flashcard));
        }
        return ids;
    }

    public static String definitionId(StudyModule.Definition definition) {
        return "def::" + definition.term;
    }

    public static String flashcardId(StudyModule.Flashcard flashcard) {
        return "fc::" + flashcard.q;
    }

    public static String quizId(StudyModule.QuizQuestion question) {
        return "quiz::" + question.question;
    }

    private void touchStudy(Progress progress) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        progress.lastStudied = now;
        LocalDate today = LocalDate.now();
        if (progress.streak.lastDate == null) {
            progress.streak.current = 1;
        } else if (progress.streak.lastDate.plusDays(1).equals(today)) {
            progress.streak.current++;
        } else if (!progress.streak.lastDate.equals(today)) {
            progress.streak.current = 1;
        }
        progress.streak.lastDate = today;
        progress.streak.longest = Math.max(progress.streak.longest, progress.streak.current);
    }
}
