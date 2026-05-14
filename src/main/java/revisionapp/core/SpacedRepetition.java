package revisionapp.core;

import revisionapp.model.Progress;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class SpacedRepetition {
    public Progress.CardProgress review(Progress.CardProgress state, String type, int stars) {
        int quality = switch (stars) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            default -> 5;
        };

        Progress.CardProgress updated = state == null ? new Progress.CardProgress() : state;
        updated.type = type;
        updated.confidence = stars;
        updated.got = quality >= 3;

        if (quality < 3) {
            updated.interval = 1;
            updated.easeFactor = Math.max(1.3, updated.easeFactor - 0.2);
        } else {
            if (updated.reviewCount == 0) {
                updated.interval = 1;
            } else if (updated.reviewCount == 1) {
                updated.interval = 6;
            } else {
                updated.interval = Math.max(1, (int) Math.round(updated.interval * updated.easeFactor));
            }
            double delta = 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02);
            updated.easeFactor = Math.max(1.3, updated.easeFactor + delta);
        }

        LocalDate today = LocalDate.now();
        updated.due = today.plusDays(updated.interval);
        updated.reviewCount++;
        updated.lastReview = OffsetDateTime.now(ZoneOffset.UTC);
        return updated;
    }

    public boolean isDue(Progress.CardProgress state) {
        if (state == null || state.due == null) {
            return true;
        }
        return !state.due.isAfter(LocalDate.now());
    }
}
