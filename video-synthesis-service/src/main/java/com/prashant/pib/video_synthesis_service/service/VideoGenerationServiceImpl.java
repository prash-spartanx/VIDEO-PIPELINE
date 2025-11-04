package com.prashant.pib.video_synthesis_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prashant.pib.video_synthesis_service.dto.GeneratedVideoResponse;
import com.prashant.pib.video_synthesis_service.entity.GeneratedVideo;
import com.prashant.pib.video_synthesis_service.entity.PressRelease;
import com.prashant.pib.video_synthesis_service.entity.User;
import com.prashant.pib.video_synthesis_service.entity.VideoStatus;
import com.prashant.pib.video_synthesis_service.repository.GeneratedVideoRepository;
import com.prashant.pib.video_synthesis_service.repository.PressReleaseRepository;
import com.prashant.pib.video_synthesis_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VideoGenerationServiceImpl implements VideoGenerationService {

    // ✅ CRITICAL: ALL fields must be FINAL for @RequiredArgsConstructor
    private final PressReleaseRepository pressReleaseRepository;
    private final GeneratedVideoRepository generatedVideoRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;  // ✅ For JSON handling

    @Value("${python.service.url:http://localhost:8000}")  // ✅ Fixed port to match FastAPI
    private String pythonServiceUrl;

    @Override
    @Transactional
    public GeneratedVideoResponse triggerVideoGeneration(Long pressReleaseId, String username) {
        // ✅ Fetch user and validate
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        PressRelease pressRelease = pressReleaseRepository.findById(pressReleaseId)
                .orElseThrow(() -> new RuntimeException("Press release not found: " + pressReleaseId));

        // ✅ Authorization check
        if (!pressRelease.getUser().getId().equals(user.getId()) &&
                !"ADMIN".equals(user.getRole())) {
            throw new RuntimeException("Unauthorized: User does not own this press release");
        }

        // ✅ Create GeneratedVideo entity with proper fields
        GeneratedVideo video = new GeneratedVideo();
        video.setPressRelease(pressRelease);
        video.setUser(user);  // ✅ Set user relationship
        video.setStatus(VideoStatus.valueOf("PENDING"));
        video.setLanguage(pressRelease.getLanguage());
        // ✅ Don't set publishedAt here - only when actually published
        video = generatedVideoRepository.save(video);  // ✅ Save to get ID

        try {
            // ✅ Call Python microservice with proper JSON
            Map<String, Object> pythonResponse = callPythonMicroservice(
                    pressRelease.getContent(),
                    pressRelease.getLanguage()
            );

            String videoUrl = (String) pythonResponse.get("video_url");
            if (videoUrl != null && !videoUrl.startsWith("http")) {
                videoUrl = pythonServiceUrl + videoUrl;  // ✅ Construct full URL
            }

            video.setVideoUrl(videoUrl);
            video.setStatus(VideoStatus.valueOf("COMPLETED"));  // ✅ Or "GENERATED"
            generatedVideoRepository.save(video);

        } catch (Exception e) {
            video.setStatus(VideoStatus.valueOf("FAILED"));
            generatedVideoRepository.save(video);
            throw new RuntimeException("Video generation failed: " + e.getMessage(), e);
        }

        return mapToResponse(video);
    }

    @Override
    public GeneratedVideoResponse getGeneratedVideo(Long id) {
        GeneratedVideo video = generatedVideoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Generated video not found: " + id));
        return mapToResponse(video);
    }

    private Map<String, Object> callPythonMicroservice(String content, String language) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // ✅ Proper JSON request body with both content and language
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("content", content);
        requestBody.put("language", language != null ? language : "en");

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
        String url = UriComponentsBuilder.fromHttpUrl(pythonServiceUrl + "/generate-video")
                .build().toUriString();

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("Python service error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Python microservice: " + e.getMessage(), e);
        }
    }

    private GeneratedVideoResponse mapToResponse(GeneratedVideo video) {
        // ✅ Use Builder pattern (preferred) or constructor
        return GeneratedVideoResponse.builder()
                .id(video.getId())
                .videoUrl(video.getVideoUrl())
                .status(String.valueOf(video.getStatus()))
                .publishedUrl(video.getPublishedUrl())
                .platform(video.getPlatform())
                .language(video.getLanguage())
                .pressReleaseId(video.getPressRelease().getId())
                .username(video.getUser().getUsername())
                .createdAt(video.getCreatedAt())
                .publishedAt(video.getPublishedAt())
                .build();

        // ✅ Alternative: Manual setter approach
        /*
        GeneratedVideoResponse response = new GeneratedVideoResponse();
        response.setId(video.getId());
        response.setVideoUrl(video.getVideoUrl());
        // ... set other fields
        return response;
        */
    }
}