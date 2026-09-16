package com.sakshigiri.youtube.cli;

import com.sakshigiri.youtube.model.Recommendation;
import com.sakshigiri.youtube.service.RecommendationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Scanner;

@Component
@ConditionalOnProperty(name = "app.cli-enabled", havingValue = "true")
public class RecommendationCli implements CommandLineRunner {

    private final RecommendationService recommendationService;

    public RecommendationCli(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Override
    public void run(String... args) {
        System.out.println("YouTube transcript recommendation demo");
        System.out.println("Type a request or 'exit' to quit.\n");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("Prompt> ");
                if (!scanner.hasNextLine()) {
                    break;
                }

                String input = scanner.nextLine().trim();
                if (input.equalsIgnoreCase("exit")) {
                    break;
                }
                if (input.isBlank()) {
                    continue;
                }

                var results = recommendationService.recommend(input, 5);

                if (results.isEmpty()) {
                    System.out.println("No transcript match found. Try a different prompt.\n");
                    continue;
                }

                System.out.println("Recommended videos:");
                for (int i = 0; i < results.size(); i++) {
                    Recommendation item = results.get(i);
                    System.out.printf(
                            "%d. %s [%s] score=%.2f%n   %s%n",
                            i + 1,
                            item.video().title(),
                            item.video().videoId(),
                            item.score(),
                            item.reason()
                    );
                }
                System.out.println();
            }
        }
    }
}
