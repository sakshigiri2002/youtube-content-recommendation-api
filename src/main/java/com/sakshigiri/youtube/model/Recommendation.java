package com.sakshigiri.youtube.model;

import java.util.List;

public record Recommendation(
        Video video,
        double score,
        String reason,
        List<String> evidence
) {}
