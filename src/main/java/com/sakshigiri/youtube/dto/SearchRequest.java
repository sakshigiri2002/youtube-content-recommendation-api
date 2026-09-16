package com.sakshigiri.youtube.dto;

import jakarta.validation.constraints.NotBlank;

public record SearchRequest(
        @NotBlank(message = "query must not be blank") String query,
        Integer limit,
        Boolean includeRoadmap
) {}
