package com.sakshigiri.youtube;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakshigiri.youtube.service.RecommendationService;
import com.sakshigiri.youtube.service.SemanticTextMatcher;
import com.sakshigiri.youtube.service.VideoCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationServiceTest {
    private RecommendationService service() throws Exception {
        VideoCatalog catalog = new VideoCatalog(new ObjectMapper(), new ClassPathResource("data/videos.json"));
        return new RecommendationService(catalog, new SemanticTextMatcher());
    }

    @Test
    void understandsAliasesAndRanksTranscriptEvidence() throws Exception {
        var results = service().recommend("I need a dessert recipe using a pressure cooker", 3);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).video().videoId()).isEqualTo("cake-cooker-101");
        assertThat(results.get(0).evidence()).isNotEmpty();
    }

    @Test
    void understandsGenericRepairAlias() throws Exception {
        var results = service().recommend("How can I fix a leaking kitchen tap?", 3);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).video().videoId()).isEqualTo("leaking-tap");
    }

    @Test
    void doesNotRecommendOvenOnlyVideoForExcludedOvenQuery() throws Exception {
        var results = service().recommend("cake using a cooker without an oven", 10);

        assertThat(results).isNotEmpty();
        assertThat(results).noneMatch(result -> result.video().videoId().equals("oven-vanilla-cake"));
    }
}
