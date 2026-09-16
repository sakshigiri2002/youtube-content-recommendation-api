package com.sakshigiri.youtube.service;

import com.sakshigiri.youtube.model.Recommendation;
import com.sakshigiri.youtube.model.Video;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Small local NLP matcher for the offline demo.
 *
 * This is more than substring matching: it builds concept profiles, expands
 * common equivalents, understands positive and negative constraints, gives
 * more weight to transcript coverage than metadata, and produces evidence
 * sentences. It is still a deterministic fallback, not a neural language
 * model. The class is the seam for a future embedding provider.
 */
@Component
public class SemanticTextMatcher {
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "for", "with", "from", "this", "that", "to", "in", "on", "of",
            "i", "need", "want", "me", "show", "video", "videos", "how", "using", "make", "learn", "please",
            "which", "what", "where", "when", "why", "do", "does", "it", "is", "are", "be", "can", "could",
            "should", "would", "find", "give", "some", "about", "please", "my"
    );
    private static final Set<String> NEGATION_WORDS = Set.of("without", "no", "not", "avoid", "excluding", "exclude", "dont", "don't", "never");

    /* Concepts are intentionally domain-neutral. This can move to a resource/config file. */
    private static final Map<String, String> CONCEPT_ALIASES = Map.ofEntries(
            Map.entry("dessert", "cake"), Map.entry("pastry", "cake"), Map.entry("sponge", "cake"),
            Map.entry("pressure cooker", "cooker"), Map.entry("stovetop", "cooker"), Map.entry("stove top", "cooker"),
            Map.entry("tubelight", "light"), Map.entry("tube light", "light"), Map.entry("lamp", "light"),
            Map.entry("fix", "repair"), Map.entry("fixing", "repair"), Map.entry("fixed", "repair"),
            Map.entry("mend", "repair"), Map.entry("troubleshoot", "repair"),
            Map.entry("recipe", "cooking"), Map.entry("bake", "cooking"), Map.entry("baking", "cooking"),
            Map.entry("cook", "cooking"), Map.entry("cooking", "cooking"),
            Map.entry("tutorial", "instruction"), Map.entry("guide", "instruction"), Map.entry("lesson", "instruction")
    );

    public QueryProfile analyze(String query) {
        String normalized = normalize(query);
        List<String> tokens = meaningfulTokens(normalized);
        Set<String> positive = new LinkedHashSet<>();
        Set<String> negative = new LinkedHashSet<>();

        for (int i = 0; i < tokens.size(); i++) {
            String token = canonical(tokens.get(i));
            if (token.isBlank()) continue;
            if (isNegated(tokens, i)) negative.add(token);
            else positive.add(token);
        }

        // Preserve meaningful multi-word concepts such as "pressure cooker".
        for (String phrase : phrases(normalized)) {
            String canonical = canonical(phrase);
            if (!canonical.isBlank()) {
                if (isPhraseNegated(normalized, phrase)) negative.add(canonical);
                else positive.add(canonical);
            }
        }

        positive.removeAll(negative);
        return new QueryProfile(query, List.copyOf(positive), List.copyOf(negative));
    }

    public Optional<Recommendation> match(QueryProfile query, Video video) {
        TextProfile title = profile(video.title());
        TextProfile metadata = profile(video.description() + " " + video.channel());
        TextProfile transcript = profile(video.transcript());

        Set<String> positive = new LinkedHashSet<>(query.positiveConcepts());
        Set<String> negative = new LinkedHashSet<>(query.negativeConcepts());
        if (positive.isEmpty()) return Optional.empty();

        Set<String> transcriptMatches = intersection(positive, transcript.concepts());
        Set<String> titleMatches = intersection(positive, title.concepts());
        Set<String> metadataMatches = intersection(positive, metadata.concepts());
        Set<String> contradictions = intersection(negative, union(title.concepts(), metadata.concepts(), transcript.concepts()));

        // Transcript evidence is the primary signal. Metadata alone cannot pass a result.
        double coverage = (double) transcriptMatches.size() / positive.size();
        double score = coverage * 0.65
                + ((double) titleMatches.size() / positive.size()) * 0.15
                + ((double) metadataMatches.size() / positive.size()) * 0.10
                + phraseBonus(positive, transcript.concepts()) * 0.10;
        score -= Math.min(0.55, contradictions.size() * 0.25);

        // Require actual transcript support and reject documents dominated by exclusions.
        if (transcriptMatches.isEmpty() || score < 0.12 || contradictions.size() >= Math.max(2, positive.size())) {
            return Optional.empty();
        }

        List<String> evidence = evidenceSentences(video.transcript(), positive, negative);
        StringBuilder reason = new StringBuilder("Transcript explains ").append(String.join(", ", transcriptMatches)).append(".");
        if (!contradictions.isEmpty()) reason.append(" Possible constraint conflict: ").append(String.join(", ", contradictions)).append(".");
        if (!evidence.isEmpty()) reason.append(" Evidence: ").append(evidence.get(0));

        return Optional.of(new Recommendation(video, round(Math.max(0, Math.min(1, score))), reason.toString(), evidence));
    }

    private List<String> evidenceSentences(String transcript, Set<String> positive, Set<String> negative) {
        return SENTENCE_SPLIT.splitAsStream(transcript)
                .map(String::trim)
                .filter(sentence -> !sentence.isBlank())
                .filter(sentence -> {
                    TextProfile profile = profile(sentence);
                    return !intersection(positive, profile.concepts()).isEmpty()
                            && intersection(negative, profile.concepts()).isEmpty();
                })
                .limit(3)
                .toList();
    }

    private boolean isNegated(List<String> tokens, int index) {
        return index > 0 && NEGATION_WORDS.contains(tokens.get(index - 1));
    }

    private boolean isPhraseNegated(String text, String phrase) {
        int start = text.indexOf(phrase);
        if (start <= 0) return false;
        String prefix = text.substring(Math.max(0, start - 12), start).trim();
        return NEGATION_WORDS.stream().anyMatch(prefix::endsWith);
    }

    private List<String> phrases(String text) {
        List<String> result = new ArrayList<>();
        String[] words = text.split(" ");
        for (int i = 0; i < words.length - 1; i++) {
            String phrase = words[i] + " " + words[i + 1];
            if (CONCEPT_ALIASES.containsKey(phrase)) result.add(phrase);
        }
        return result;
    }

    private TextProfile profile(String text) {
        String normalized = normalize(text);
        Set<String> concepts = new LinkedHashSet<>(meaningfulTokens(normalized));
        phrases(normalized).forEach(phrase -> concepts.add(canonical(phrase)));
        concepts.replaceAll(this::canonical);
        concepts.removeIf(String::isBlank);
        return new TextProfile(concepts);
    }

    private List<String> meaningfulTokens(String text) {
        return Arrays.stream(text.split(" "))
                .map(this::canonical)
                .filter(token -> token.length() >= 2 && !STOP_WORDS.contains(token))
                .toList();
    }

    private String canonical(String token) {
        String value = token == null ? "" : token.toLowerCase(Locale.ROOT).trim();
        if (value.isBlank()) return "";
        String alias = CONCEPT_ALIASES.get(value);
        if (alias != null) return alias;
        if (value.endsWith("ies") && value.length() > 4) return value.substring(0, value.length() - 3) + "y";
        if (value.endsWith("ing") && value.length() > 5) return value.substring(0, value.length() - 3);
        if (value.endsWith("ed") && value.length() > 4) return value.substring(0, value.length() - 2);
        if (value.endsWith("s") && value.length() > 3) return value.substring(0, value.length() - 1);
        return value;
    }

    private String normalize(String text) {
        return NON_ALPHANUMERIC.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT)).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private Set<String> intersection(Collection<String> left, Collection<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return result;
    }

    @SafeVarargs
    private final Set<String> union(Collection<String>... values) {
        Set<String> result = new LinkedHashSet<>();
        for (Collection<String> value : values) result.addAll(value);
        return result;
    }

    private double phraseBonus(Set<String> query, Set<String> document) {
        return query.stream().anyMatch(term -> term.contains(" ") && document.contains(term)) ? 1.0 : 0.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record QueryProfile(String originalQuery, List<String> positiveConcepts, List<String> negativeConcepts) {}
    private record TextProfile(Set<String> concepts) {}
}
