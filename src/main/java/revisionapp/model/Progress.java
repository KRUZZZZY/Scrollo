package revisionapp.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Progress {
    public String moduleId;
    public String moduleVersion;
    public OffsetDateTime lastStudied;
    public Streak streak = new Streak();
    public Map<String, CardProgress> cards = new LinkedHashMap<>();
    public List<QuizAttempt> quizHistory = new ArrayList<>();
    public Map<String, CardProgress> archived = new LinkedHashMap<>();

    public static class Streak {
        public int current = 0;
        public int longest = 0;
        public LocalDate lastDate;
    }

    public static class CardProgress {
        public String type;
        public boolean got;
        public int confidence;
        public int interval = 1;
        public double easeFactor = 2.5;
        public LocalDate due;
        public int reviewCount = 0;
        public OffsetDateTime lastReview;
    }

    public static class QuizAttempt {
        public OffsetDateTime timestamp;
        public int score;
        public int total;
        public List<String> topics = new ArrayList<>();
        public List<String> wrongIds = new ArrayList<>();
    }
}
