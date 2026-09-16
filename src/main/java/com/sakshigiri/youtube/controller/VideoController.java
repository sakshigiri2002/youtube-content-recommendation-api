package com.sakshigiri.youtube.controller;

import com.sakshigiri.youtube.model.Video;
import com.sakshigiri.youtube.service.VideoCatalog;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class VideoController {

    private final VideoCatalog catalog;

    public VideoController(VideoCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/videos")
    public List<Video> getAllVideos() {
        return catalog.all();
    }

    @GetMapping("/videos/{videoId}")
    public ResponseEntity<Video> getVideo(@PathVariable String videoId) {
        return catalog.all().stream()
                .filter(video -> video.videoId().equals(videoId))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
