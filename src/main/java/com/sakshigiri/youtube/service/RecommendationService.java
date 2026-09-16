package com.sakshigiri.youtube.service;

import com.sakshigiri.youtube.model.Recommendation;
import com.sakshigiri.youtube.model.Video;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RecommendationService {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "for", "with", "from", "this", "that", "to", "in",
            "on", "of", "i", "need", "want", "me", "show", "video", "videos", "how", "using",
            "make", "learn", "type", "please", "which", "what", "where", "when", "why",
            "do", "does", "it", "is", "are", "be", "can", "could", "should", "would"
    );

    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9 ]");

    private final VideoCatalog catalog;

    public RecommendationService(VideoCatalog catalog) {
        this.catalog = catalog;
    }

    public List<String> extractTerms(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        String cleaned = NON_WORD.matcher(normalized).replaceAll(" ");

        return Arrays.stream(cleaned.split("\\s+"))
                .filter(token -> token.length() >= 3)
                .filter(token -> !STOP_WORDS.contains(token))
                .distinct()
                .limit(20)
                .toList();
    }

    public List<Recommendation> recommend(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String queryLower = query.toLowerCase(Locale.ROOT);
        List<String> terms = extractTerms(query);

        List<Recommendation> results = new ArrayList<>();
        for (Video video : catalog.all()) {
            Recommendation recommendation = scoreVideo(video, queryLower, terms);
            if (recommendation != null) {
                results.add(recommendation);
            }
        }

        return results.stream()
                .sorted(Comparator.comparingDouble(Recommendation::score).reversed())
                .limit(Math.max(1, Math.min(20, maxResults)))
                .toList();
    }

    private Recommendation scoreVideo(Video video, String queryLower, List<String> terms) {
        String titleLower = video.title().toLowerCase(Locale.ROOT);
        String descriptionLower = video.description().toLowerCase(Locale.ROOT);
        String transcriptLower = video.transcript().toLowerCase(Locale.ROOT);
        String searchable = titleLower + " " + descriptionLower + " " + transcriptLower;

        List<String> evidence = terms.stream()
                .filter(term -> searchable.contains(term))
                .distinct()
                .limit(8)
                .toList();

        if (evidence.isEmpty()) {
            return null;
        }

        long titleMatches = terms.stream().filter(term -> titleLower.contains(term)).count();
        long transcriptMatches = terms.stream().filter(term -> transcriptLower.contains(term)).count();
        long descriptionMatches = terms.stream().filter(term -> descriptionLower.contains(term)).count();

        double score = 0.0;
        score += titleMatches * 0.18;
        score += transcriptMatches * 0.15;
        score += descriptionMatches * 0.10;
        score += Math.min(0.5, (double) evidence.size() / Math.max(1, terms.size()) * 0.60);

        boolean asksWithoutOven = queryLower.contains("without oven") || queryLower.contains("no oven") || queryLower.contains("without an oven");
        boolean ovenOnlyVideo = titleLower.contains("oven") || transcriptLower.contains("oven") || descriptionLower.contains("oven");
        if (asksWithoutOven && ovenOnlyVideo && !(titleLower.contains("without") && titleLower.contains("oven") && titleLower.contains("without an oven"))) {
            score -= 0.55;
        }

        if (score <= 0.05) {
            return null;
        }

        String reason = "Matches query terms: " + String.join(", ", evidence) + ".";
        if (asksWithoutOven && !ovenOnlyVideo) {
            reason += " It also avoids the excluded oven requirement.";
        }

        return new Recommendation(
                video,
                Math.round(Math.min(1.0, score) * 100.0) / 100.0,
                reason,
                evidence
        );
    }
}
