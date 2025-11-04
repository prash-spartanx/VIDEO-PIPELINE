package com.prashant.pib.video_synthesis_service.service;

import com.prashant.pib.video_synthesis_service.dto.*;
import com.prashant.pib.video_synthesis_service.entity.*;
import com.prashant.pib.video_synthesis_service.repository.*;
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

    // ✅ Define the base URL that your frontend can access for videos
    @Value("${video.public-base-url:http://localhost:5001/videos}")
    private String videoPublicBaseUrl;

    // ----------------------------
    // POLLER
    // ----------------------------
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

    // ----------------------------
    // GENERATE VIDEO
    // ----------------------------
    @Override
    @Transactional
    public GeneratedVideoResponse generateVideo(Long pressReleaseId, String username) {
        log.info("Initiating video generation job for press release ID: {}", pressReleaseId);
        PressRelease pressRelease = pressReleaseRepository.findById(pressReleaseId)
                .orElseThrow(() -> new RuntimeException("Press release not found: " + pressReleaseId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        PythonVideoRequest pythonRequest = new PythonVideoRequest(pressRelease.getContent(), pressRelease.getLanguage());
        HttpEntity<PythonVideoRequest> requestEntity = new HttpEntity<>(pythonRequest, createJsonHeaders());

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
        video.setLanguage(pressRelease.getLanguage());

        GeneratedVideo savedVideo = generatedVideoRepository.save(video);
        log.info("Saved video record with ID {} and status PROCESSING", savedVideo.getId());

        return mapToResponse(savedVideo);
    }

    // ----------------------------
    // GET VIDEO STATUS BY JOB ID
    // ----------------------------
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

    // ----------------------------
    // UPDATE STATUS HELPER
    // ----------------------------
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
                    // ✅ Extract just the filename from Python's response
                    String pythonUrl = statusResponse.getVideo_url();
                    String filename = pythonUrl.substring(pythonUrl.lastIndexOf('/') + 1);
                   
                    // ✅ Construct the public URL
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
                default:
                    log.warn("Received unknown status '{}' for job ID {}", statusResponse.getStatus(), video.getJobId());
                    break;
            }
        } catch (RestClientException e) {
            log.error("Error polling job {}: {}", video.getJobId(), e.getMessage());
        }
    }

    // ----------------------------
    // GETTERS / PUBLISHING
    // ----------------------------
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

        video.setStatus(VideoStatus.PUBLISHED);
        video.setPublishedUrl(request.getPublishedUrl());
        video.setPlatform(request.getPlatform());
        video.setPublishedAt(LocalDateTime.now());

        GeneratedVideo saved = generatedVideoRepository.save(video);
        return mapToResponse(saved);
    }

    @Override
    public String improviseContent(Long pressReleaseId, String username) {
        PressRelease pressRelease = pressReleaseRepository.findById(pressReleaseId)
                .orElseThrow(() -> new RuntimeException("Press release not found: " + pressReleaseId));
        return "Improvised version of: " + pressRelease.getTitle();
    }
    @Override
public GeneratedVideoResponse getVideoById(Long id, String language) {
    // Example implementation — you can modify it as needed
    Optional<GeneratedVideo> videoOpt = generatedVideoRepository.findById(id);

    if (videoOpt.isEmpty()) {
        throw new RuntimeException("Video not found with ID: " + id);
    }

    GeneratedVideo video = videoOpt.get();

    return mapToResponse(video);
}


    // ----------------------------
    // HELPERS
    // ----------------------------
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