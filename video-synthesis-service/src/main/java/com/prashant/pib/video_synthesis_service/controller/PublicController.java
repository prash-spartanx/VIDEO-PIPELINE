package com.prashant.pib.video_synthesis_service.controller;

import com.prashant.pib.video_synthesis_service.dto.GeneratedVideoResponse;
import com.prashant.pib.video_synthesis_service.service.GeneratedVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final GeneratedVideoService generatedVideoService;

    // Anyone (no auth) can see the published gallery
    @GetMapping("/videos")
    public ResponseEntity<List<GeneratedVideoResponse>> getPublishedVideos() {
        return ResponseEntity.ok(generatedVideoService.getPublishedVideos());
    }
}
