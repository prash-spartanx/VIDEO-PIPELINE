package com.prashant.pib.video_synthesis_service.service;

import com.prashant.pib.video_synthesis_service.dto.PibPressReleaseDto;
import com.prashant.pib.video_synthesis_service.dto.PressReleaseRequest;
import com.prashant.pib.video_synthesis_service.dto.PressReleaseResponse;

import java.util.List;

public interface PressReleaseService {
    PressReleaseResponse createPressRelease(PressReleaseRequest request, String username);
    PressReleaseResponse getPressRelease(Long id);
    List<PressReleaseResponse> getAllPressReleases();
    List<PressReleaseResponse> getPressReleasesByUser(String username);
    List<PressReleaseResponse> saveFetchedPressReleases(List<PibPressReleaseDto> dtos, String username);
}
