package com.prashant.pib.video_synthesis_service.service;

import com.prashant.pib.video_synthesis_service.dto.PibPressReleaseDto;

import java.util.List;

public interface PibFetcherService {
    /** Try to acquire a single-flight lock for fetching. Returns true if acquired. */
    boolean tryStartFetch();

    /** Release the fetch lock. */
    void finishFetch();

    /** Fetch latest PIB press releases (capped & deduped). */
    List<PibPressReleaseDto> fetchLatestPressReleases();
}
