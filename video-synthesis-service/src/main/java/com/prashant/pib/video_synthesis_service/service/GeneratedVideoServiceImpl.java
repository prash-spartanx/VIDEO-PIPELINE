package com.prashant.pib.video_synthesis_service.service;

import com.prashant.pib.video_synthesis_service.dto.GeneratedVideoResponse;
import com.prashant.pib.video_synthesis_service.dto.PublishVideoRequest;
import com.prashant.pib.video_synthesis_service.dto.PythonJobResponse;
import com.prashant.pib.video_synthesis_service.dto.PythonStatusResponse;
import com.prashant.pib.video_synthesis_service.entity.GeneratedVideo;
import com.prashant.pib.video_synthesis_service.entity.PressRelease;
import com.prashant.pib.video_synthesis_service.entity.User;
import com.prashant.pib.video_synthesis_service.entity.VideoStatus;
import com.prashant.pib.video_synthesis_service.repository.GeneratedVideoRepository;
import com.prashant.pib.video_synthesis_service.repository.PressReleaseRepository;
import com.prashant.pib.video_synthesis_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeneratedVideoServiceImpl implements GeneratedVideoService {

    private final GeneratedVideoRepository generatedVideoRepository;
    private final PressReleaseRepository pressReleaseRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Value("${python.service.base-url}")
    private String pythonServiceBaseUrl;

    @Value("${video.public-base-url:http://localhost:5001/videos}")
    private String videoPublicBaseUrl;

    // ---------- POLLER ----------
    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void pollAndUpdateVideoStatuses() {
        log.info("POLLER: Running scheduled task to update video statuses.");
        List<GeneratedVideo> processingVideos = generatedVideoRepository.findByStatus(VideoStatus.PROCESSING);
        if (processingVideos.isEmpty()) {
            log.info("POLLER: No videos in PROCESSING state. Task complete.");
            return;
        }
        for (GeneratedVideo video : processingVideos) {
            log.info("POLLER: Checking status for video ID {} (Job ID: {})", video.getId(), video.getJobId());
            updateVideoStatus(video);
        }
        log.info("POLLER: Finished status check for all processing videos.");
    }

    // ---------- GENERATE ----------
    @Override
    @Transactional
    public GeneratedVideoResponse generateVideo(Long pressReleaseId, String username) {
        return generateVideo(pressReleaseId, username, null, null);
    }

    @Override
    @Transactional
    public GeneratedVideoResponse generateVideo(Long pressReleaseId, String username, String languageOverride, String scriptOverride) {
        log.info("Initiating video generation job for press release ID: {} (langOverride={}, scriptOverride={})",
                pressReleaseId, languageOverride, scriptOverride != null);

        PressRelease pressRelease = pressReleaseRepository.findById(pressReleaseId)
                .orElseThrow(() -> new RuntimeException("Press release not found: " + pressReleaseId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        String language = (languageOverride != null && !languageOverride.isBlank())
                ? languageOverride
                : (pressRelease.getLanguage() != null ? pressRelease.getLanguage() : "en");

        record PythonVideoReq(String content, String language, String script_override) {}
        PythonVideoReq pythonRequest = new PythonVideoReq(
                pressRelease.getContent(),
                language,
                (scriptOverride != null && !scriptOverride.isBlank()) ? scriptOverride : null
        );

        HttpEntity<PythonVideoReq> requestEntity = new HttpEntity<>(pythonRequest, createJsonHeaders());
        String url = pythonServiceBaseUrl + "/generate-video";
        PythonJobResponse pythonResponse;
        try {
            pythonResponse = restTemplate.postForObject(url, requestEntity, PythonJobResponse.class);
            if (pythonResponse == null || pythonResponse.getJob_id() == null) {
                throw new IllegalStateException("Python service returned invalid job submission response.");
            }
        } catch (RestClientException e) {
            log.error("FATAL: Failed to submit job to Python video service. URL: {}", url, e);
            throw new RuntimeException("Error communicating with the video generation service.", e);
        }

        log.info("Python service accepted job with ID: {}", pythonResponse.getJob_id());

        GeneratedVideo video = new GeneratedVideo();
        video.setPressRelease(pressRelease);
        video.setUser(user);
        video.setStatus(VideoStatus.PROCESSING);
        video.setJobId(pythonResponse.getJob_id());
        video.setLanguage(language);

        GeneratedVideo savedVideo = generatedVideoRepository.save(video);
        log.info("Saved video record with ID {} and status PROCESSING", savedVideo.getId());

        return mapToResponse(savedVideo);
    }

    // ---------- STATUS BY JOB ----------
    @Override
    @Transactional
    public GeneratedVideoResponse getVideoStatusByJobId(String jobId) {
        log.info("Fetching status for job ID: {}", jobId);
        GeneratedVideo video = generatedVideoRepository.findByJobId(jobId)
                .orElseThrow(() -> new RuntimeException("No video generation job found with ID: " + jobId));

        if (video.getStatus() == VideoStatus.PROCESSING) {
            updateVideoStatus(video);
        }
        return mapToResponse(video);
    }

    private void updateVideoStatus(GeneratedVideo video) {
        String url = pythonServiceBaseUrl + "/video-status/" + video.getJobId();
        try {
            PythonStatusResponse statusResponse = restTemplate.getForObject(url, PythonStatusResponse.class);
            if (statusResponse == null) {
                log.warn("Received null status response from Python for job ID {}", video.getJobId());
                return;
            }
            switch (statusResponse.getStatus()) {
                case "completed":
                    video.setStatus(VideoStatus.COMPLETED);
                    String pythonUrl = statusResponse.getVideo_url();
                    String filename = pythonUrl.substring(pythonUrl.lastIndexOf('/') + 1);
                    String publicUrl = videoPublicBaseUrl + "/" + filename;
                    video.setVideoUrl(publicUrl);
                    log.info("✅ Video {} marked COMPLETED. Public URL: {}", video.getId(), publicUrl);
                    break;
                case "failed":
                    video.setStatus(VideoStatus.FAILED);
                    video.setErrorMessage(statusResponse.getMessage());
                    log.error("❌ Video ID {} failed: {}", video.getId(), statusResponse.getMessage());
                    break;
                case "processing":
                    log.info("Video ID {} still processing...", video.getId());
                    break;
                case "pending":
                    log.info("Video ID {} still pending...", video.getId());
                    break;
                default:
                    log.warn("Received unknown status '{}' for job ID {}", statusResponse.getStatus(), video.getJobId());
                    break;
            }
        } catch (RestClientException e) {
            log.error("Error polling job {}: {}", video.getJobId(), e.getMessage());
        }
    }

    // ---------- GETTERS / PUBLISH ----------
    @Override
    @Transactional(readOnly = true)
    public List<GeneratedVideoResponse> getVideosForCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return generatedVideoRepository.findByUserId(user.getId())
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GeneratedVideoResponse> getAllVideos() {
        return generatedVideoRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GeneratedVideoResponse publishVideo(Long videoId, PublishVideoRequest request, String username) {
        GeneratedVideo video = generatedVideoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found: " + videoId));

        if (video.getStatus() != VideoStatus.COMPLETED) {
            throw new IllegalStateException("Video must be COMPLETED before publishing.");
        }

        // ✅ Safe defaults so empty {} works
        String finalPlatform = (request != null && request.getPlatform() != null && !request.getPlatform().isBlank())
                ? request.getPlatform()
                : "INTERNAL";

        String finalPublishedUrl = (request != null && request.getPublishedUrl() != null && !request.getPublishedUrl().isBlank())
                ? request.getPublishedUrl()
                : video.getVideoUrl();

        video.setStatus(VideoStatus.PUBLISHED);
        video.setPublishedUrl(finalPublishedUrl);
        video.setPlatform(finalPlatform);
        video.setPublishedAt(LocalDateTime.now());

        GeneratedVideo saved = generatedVideoRepository.save(video);
        return mapToResponse(saved);
    }

    @Override
    public String improviseContent(Long pressReleaseId, String username, String language, String styleHints) {
        PressRelease pressRelease = pressReleaseRepository.findById(pressReleaseId)
                .orElseThrow(() -> new RuntimeException("Press release not found: " + pressReleaseId));

        String resolvedLang = (language != null && !language.isBlank())
                ? language
                : (pressRelease.getLanguage() != null ? pressRelease.getLanguage() : "en");

        record ImproviseReq(String content, String language, String style_hints) {}
        ImproviseReq body = new ImproviseReq(
                pressRelease.getContent(),
                resolvedLang,
                (styleHints != null && !styleHints.isBlank()) ? styleHints : null
        );

        String url = pythonServiceBaseUrl + "/improvise";
        try {
            HttpEntity<ImproviseReq> entity = new HttpEntity<>(body, createJsonHeaders());
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(url, entity, Map.class);
            if (resp == null || !resp.containsKey("improved_script")) {
                throw new IllegalStateException("Invalid improvise response");
            }
            return String.valueOf(resp.get("improved_script"));
        } catch (Exception e) {
            log.error("Improvise failed via Python: {}", e.getMessage(), e);
            throw new RuntimeException("Improvise failed: " + e.getMessage(), e);
        }
    }

    @Override
    public GeneratedVideoResponse getVideoById(Long videoId, String username) {
        GeneratedVideo video = generatedVideoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found with ID: " + videoId));
        return mapToResponse(video);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GeneratedVideoResponse> getPublishedVideos() {
        return generatedVideoRepository.findByStatus(VideoStatus.PUBLISHED)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ---------- HELPERS ----------
    private HttpHeaders createJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private GeneratedVideoResponse mapToResponse(GeneratedVideo video) {
        return GeneratedVideoResponse.builder()
                .id(video.getId())
                .videoUrl(video.getVideoUrl())
                .status(video.getStatus().toString())
                .publishedUrl(video.getPublishedUrl())
                .platform(video.getPlatform())
                .language(video.getLanguage())
                .pressReleaseId(video.getPressRelease().getId())
                .username(video.getUser().getUsername())
                .jobId(video.getJobId())
                .errorMessage(video.getErrorMessage())
                .createdAt(video.getCreatedAt())
                .publishedAt(video.getPublishedAt())
                .build();
    }
}
