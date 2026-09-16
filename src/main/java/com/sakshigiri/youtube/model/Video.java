package com.sakshigiri.youtube.model;

public record Video(
        String videoId,
        String title,
        String channel,
        String description,
        String language,
        long durationSeconds,
        String transcript
) {}
