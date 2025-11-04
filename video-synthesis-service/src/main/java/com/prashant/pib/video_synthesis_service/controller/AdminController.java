package com.prashant.pib.video_synthesis_service.controller;

import com.prashant.pib.video_synthesis_service.dto.PibPressReleaseDto;
import com.prashant.pib.video_synthesis_service.dto.PressReleaseResponse;
import com.prashant.pib.video_synthesis_service.dto.GeneratedVideoResponse;
import com.prashant.pib.video_synthesis_service.dto.PublishVideoRequest;
import com.prashant.pib.video_synthesis_service.service.GeneratedVideoService;
import com.prashant.pib.video_synthesis_service.service.PibFetcherService;
import com.prashant.pib.video_synthesis_service.service.PressReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final PibFetcherService pibFetcherService;
    private final PressReleaseService pressReleaseService;
    private final GeneratedVideoService generatedVideoService;

    @PostMapping("/fetch-latest-prs")
    public ResponseEntity<List<PressReleaseResponse>> fetchAndSaveLatestPressReleases(Authentication authentication) {
        String username = authentication.getName();
        log.info("Admin {} initiated PIB fetch", username);

        try {
            List<PibPressReleaseDto> dtos = pibFetcherService.fetchLatestPressReleases();
            log.info("Fetched {} DTOs from PIB", dtos.size());
            List<PressReleaseResponse> saved = pressReleaseService.saveFetchedPressReleases(dtos, username);
            log.info("Admin {} saved {} new press releases", username, saved.size());
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Failed to fetch PIB data for {}: {}", username, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/latest-prs")
    public ResponseEntity<List<PressReleaseResponse>> getLatestPressReleases() {
        List<PressReleaseResponse> all = pressReleaseService.getAllPressReleases();
        log.info("Returning {} press releases for dashboard", all.size());
        return ResponseEntity.ok(all);
    }

    @PostMapping("/generate")
    public ResponseEntity<GeneratedVideoResponse> generateVideo(
            @RequestParam Long pressReleaseId, Authentication authentication) {
        String username = authentication.getName();
        GeneratedVideoResponse response = generatedVideoService.generateVideo(pressReleaseId, username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/video/status/{jobId}")
    public ResponseEntity<GeneratedVideoResponse> getVideoStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(generatedVideoService.getVideoStatusByJobId(jobId));
    }

    @GetMapping("/press-release/{id}")
    public ResponseEntity<PressReleaseResponse> getPressReleaseById(@PathVariable Long id) {
        return ResponseEntity.ok(pressReleaseService.getPressRelease(id));
    }

    @PostMapping("/publish")
    public ResponseEntity<GeneratedVideoResponse> publishVideo(
            @RequestParam Long videoId,
            @RequestBody PublishVideoRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(generatedVideoService.publishVideo(videoId, request, username));
    }

    @PostMapping("/improvise")
    public ResponseEntity<String> improviseContent(
            @RequestParam Long pressReleaseId,
            Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(generatedVideoService.improviseContent(pressReleaseId, username));
    }
}