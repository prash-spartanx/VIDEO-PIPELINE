package com.prashant.pib.video_synthesis_service.service;

import com.prashant.pib.video_synthesis_service.dto.GeneratedVideoResponse;

public interface VideoGenerationService {
    GeneratedVideoResponse triggerVideoGeneration(Long pressReleaseId, String username);
    GeneratedVideoResponse getGeneratedVideo(Long id);
}