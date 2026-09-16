package com.sakshigiri.youtube;

import com.sakshigiri.youtube.service.RecommendationService;
import com.sakshigiri.youtube.service.VideoCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationServiceTest {

    @Test
    void shouldRankCookerCakeVideoHigherThanOvenOnlyVideo() throws Exception {
        VideoCatalog catalog = new VideoCatalog(new com.fasterxml.jackson.databind.ObjectMapper(), new ClassPathResource("data/videos.json"));
        RecommendationService service = new RecommendationService(catalog);

        var results = service.recommend("I need videos to make cake using a cooker without an oven", 3);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).video().videoId()).isEqualTo("cake-cooker-101");
    }
}
