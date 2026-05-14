package revisionapp.model;

public class Settings {
    public String activeTheme = "dark";
    public String activeModule = "cs375-logic";
    public String fontSize = "medium";
    public String fontFamily = "default";
    public int studyDailyGoal = 20;
    public Lcwc lcwc = new Lcwc();
    public Flashcards flashcards = new Flashcards();
    public Quiz quiz = new Quiz();
    public Spaced spaced = new Spaced();
    public Accessibility accessibility = new Accessibility();

    public int baseFontPx() {
        return switch (fontSize == null ? "medium" : fontSize) {
            case "small" -> 11;
            case "large" -> 15;
            case "xlarge" -> 17;
            default -> 13;
        };
    }

    public static class Lcwc {
        public int lookTimeSec = 5;
        public int coverTimeSec = 0;
        public int writeTimeSec = 0;
        public int revealDelaySec = 0;
        public boolean autoAdvance = false;
        public boolean useFlashcardMode = false;
    }

    public static class Flashcards {
        public int autoAdvanceSec = 0;
        public boolean showConfidenceBar = true;
        public boolean useLookCoverWriteCheck = false;
    }

    public static class Quiz {
        public boolean showExplanationOnWrong = true;
        public boolean showWrongReviewAtEnd = true;
    }

    public static class Spaced {
        public boolean enabled = true;
        public int dailyNewCards = 10;
    }

    public static class Accessibility {
        public boolean reduceMotion = false;
        public boolean highContrast = false;
        public boolean dyslexicFont = false;
    }
}
