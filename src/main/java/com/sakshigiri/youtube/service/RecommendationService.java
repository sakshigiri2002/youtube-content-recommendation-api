package com.sakshigiri.youtube.service;

import com.sakshigiri.youtube.model.Recommendation;
import com.sakshigiri.youtube.model.Video;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Orchestrates intent-aware, transcript-grounded recommendation.
 * The matcher is deliberately isolated so it can later be replaced by
 * embedding/vector retrieval without changing the API layer.
 */
@Service
public class RecommendationService {
    private final VideoCatalog catalog;
    private final SemanticTextMatcher matcher;

    public RecommendationService(VideoCatalog catalog, SemanticTextMatcher matcher) {
        this.catalog = catalog;
        this.matcher = matcher;
    }

    public List<String> extractTerms(String query) {
        return matcher.analyze(query).positiveConcepts().stream().limit(20).toList();
    }

    public List<Recommendation> recommend(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        SemanticTextMatcher.QueryProfile profile = matcher.analyze(query);
        List<Recommendation> results = new ArrayList<>();
        for (Video video : catalog.all()) {
            matcher.match(profile, video).ifPresent(results::add);
        }

        return results.stream()
                .sorted(Comparator.comparingDouble(Recommendation::score).reversed()
                        .thenComparing(r -> r.video().title().toLowerCase(Locale.ROOT)))
                .limit(Math.max(1, Math.min(20, maxResults)))
                .toList();
    }
}
