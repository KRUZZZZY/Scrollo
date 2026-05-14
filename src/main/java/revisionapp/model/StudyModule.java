package revisionapp.model;

import java.util.ArrayList;
import java.util.List;

public class StudyModule {
    public String id;
    public String name;
    public String institution;
    public String version;
    public String description;
    public List<Category> categories = new ArrayList<>();
    public List<Definition> definitions = new ArrayList<>();
    public List<Flashcard> flashcards = new ArrayList<>();
    public List<QuizQuestion> quiz = new ArrayList<>();
    public List<KeyValue> quickReference = new ArrayList<>();
    public List<String> examTraps = new ArrayList<>();
    public List<String> lcwcExclude = new ArrayList<>();

    public Category categoryFor(String key) {
        return categories.stream()
                .filter(category -> category.key != null && category.key.equals(key))
                .findFirst()
                .orElseGet(Category::fallback);
    }

    public static class Category {
        public String key;
        public String label;
        public ColorTokens color = new ColorTokens();

        public static Category fallback() {
            Category category = new Category();
            category.key = "misc";
            category.label = "Misc";
            category.color.bg = "rgba(148,163,184,0.15)";
            category.color.border = "#94a3b8";
            category.color.text = "#cbd5e1";
            return category;
        }
    }

    public static class ColorTokens {
        public String bg;
        public String border;
        public String text;
    }

    public static class Definition {
        public String topic;
        public String term;
        public String def;
    }

    public static class Flashcard {
        public String topic;
        public String q;
        public String a;
        public List<String> tags = new ArrayList<>();
    }

    public static class QuizQuestion {
        public String topic;
        public String question;
        public List<String> options = new ArrayList<>();
        public int answer;
        public String explanation;
    }

    public static class KeyValue {
        public String key;
        public String value;
    }
}
