package com.queryflow.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * TrendingScoreService computes trending scores for search queries.
 *
 * Why popularity alone is insufficient:
 * Popularity-only ranking (sorting purely by search count) creates a feedback loop where historical,
 * high-volume searches (like "iphone") permanently dominate the trending list. This makes the system
 * static and prevents newly emerging searches with lower raw volume from surfacing.
 *
 * Why recency matters:
 * Recency reflects the current interest of users. If a query is searched frequently right now (recent activity),
 * it signifies active interest. Combining recency and popularity allows the system to identify
 * breaking trends or viral queries that have a high rate of searches recently, even if their total historical
 * count is still smaller than established giants.
 *
 * How scores are calculated:
 * - Popularity Score: Normalized count against the maximum count in the candidate set:
 *   popularityScore = count / maxCount (range 0.0 to 1.0)
 * - Recency Score: Inverse decay based on hours since last search:
 *   recencyScore = 1.0 / (hoursSinceLastSearch + 1.0) (range 0.0 to 1.0)
 * - Final Score: Weighted sum of popularity and recency scores:
 *   finalScore = (popularityWeight * popularityScore) + (recencyWeight * recencyScore)
 */
@Service
@Slf4j
public class TrendingScoreService {

    @Value("${queryflow.trending.popularity-weight:0.7}")
    private double popularityWeight;

    @Value("${queryflow.trending.recency-weight:0.3}")
    private double recencyWeight;

    public double calculatePopularityScore(long count, long maxCount) {
        if (maxCount <= 0) {
            return 0.0;
        }
        return (double) count / maxCount;
    }

    public double calculateRecencyScore(LocalDateTime lastSearched, LocalDateTime now) {
        if (lastSearched == null) {
            return 0.0;
        }
        if (lastSearched.isAfter(now)) {
            // Guard against clock drift or future dates
            return 1.0;
        }
        double hours = Duration.between(lastSearched, now).toMillis() / 3600000.0;
        return 1.0 / (hours + 1.0);
    }

    public double calculateFinalScore(double popularityScore, double recencyScore) {
        return (popularityWeight * popularityScore) + (recencyWeight * recencyScore);
    }

    public double getPopularityWeight() {
        return popularityWeight;
    }

    public double getRecencyWeight() {
        return recencyWeight;
    }
}
