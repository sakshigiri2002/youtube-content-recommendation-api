package com.sakshigiri.youtube.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakshigiri.youtube.model.Video;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class VideoCatalog {
    private final List<Video> videos;

    public VideoCatalog(ObjectMapper objectMapper,
                       @Value("${app.data-file:classpath:data/videos.json}") Resource dataFile) throws IOException {
        try (InputStream input = dataFile.getInputStream()) {
            this.videos = List.copyOf(objectMapper.readValue(input, new TypeReference<List<Video>>() {}));
        }
    }

    public List<Video> all() {
        return videos;
    }
}
