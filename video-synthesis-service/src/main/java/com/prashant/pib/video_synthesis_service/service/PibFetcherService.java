package com.prashant.pib.video_synthesis_service.service;

import com.prashant.pib.video_synthesis_service.dto.PibPressReleaseDto;
import java.util.List;

public interface PibFetcherService {
    List<PibPressReleaseDto> fetchLatestPressReleases();
}