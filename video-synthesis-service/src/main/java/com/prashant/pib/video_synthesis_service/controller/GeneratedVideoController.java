package com.prashant.pib.video_synthesis_service.controller;

import com.prashant.pib.video_synthesis_service.dto.GeneratedVideoResponse;
import com.prashant.pib.video_synthesis_service.service.GeneratedVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class GeneratedVideoController {

    private final GeneratedVideoService generatedVideoService;

    /**
     * Endpoint for a logged-in user to retrieve a list of all videos they have generated.
     * Accessible by both USER and ADMIN roles.
     */
    @GetMapping("/my-videos")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<GeneratedVideoResponse>> getMyVideos(Authentication authentication) {
        String username = authentication.getName();
        List<GeneratedVideoResponse> videos = generatedVideoService.getVideosForCurrentUser(username);
        return ResponseEntity.ok(videos);
    }

    /**
     * Endpoint to retrieve a single video by its ID.
     * Authorization is handled in the service layer:
     * - Allows access if the video is PUBLISHED.
     * - Allows access if the requester is an ADMIN.
     * - Allows access if the requester is the owner of the video.
     * Accessible by both USER and ADMIN roles.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<GeneratedVideoResponse> getGeneratedVideoById(@PathVariable Long id, Authentication authentication) {
        String username = authentication.getName();
        GeneratedVideoResponse response = generatedVideoService.getVideoById(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint for an ADMIN to retrieve a list of every video in the system.
     * This endpoint is restricted to ADMINS ONLY.
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<GeneratedVideoResponse>> getAllVideos() {
        List<GeneratedVideoResponse> videos = generatedVideoService.getAllVideos();
        return ResponseEntity.ok(videos);
    }
}