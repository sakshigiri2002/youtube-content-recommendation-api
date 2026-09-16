package com.sakshigiri.youtube.dto;

import com.sakshigiri.youtube.model.Recommendation;
import java.util.List;

public record SearchResponse(
        String query,
        List<String> extractedTerms,
        List<Recommendation> recommendations,
        List<String> recommendedRoadmap,
        long processingTimeMs
) {}
