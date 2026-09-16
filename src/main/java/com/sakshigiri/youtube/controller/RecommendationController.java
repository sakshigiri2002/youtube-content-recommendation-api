package com.sakshigiri.youtube.controller;

import com.sakshigiri.youtube.dto.SearchRequest;
import com.sakshigiri.youtube.dto.SearchResponse;
import com.sakshigiri.youtube.model.Recommendation;
import com.sakshigiri.youtube.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/recommendations/search")
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        long start = System.currentTimeMillis();
        int limit = request.limit() == null ? 5 : Math.max(1, Math.min(20, request.limit()));

        List<String> terms = recommendationService.extractTerms(request.query());
        List<Recommendation> recommendations = recommendationService.recommend(request.query(), limit);

        List<String> roadmap = Boolean.TRUE.equals(request.includeRoadmap()) && !recommendations.isEmpty()
                ? recommendations.stream().map(r -> r.video().title()).limit(3).toList()
                : List.of();

        SearchResponse response = new SearchResponse(
                request.query(),
                terms,
                recommendations,
                roadmap,
                System.currentTimeMillis() - start
        );

        return ResponseEntity.ok(response);
    }
}
