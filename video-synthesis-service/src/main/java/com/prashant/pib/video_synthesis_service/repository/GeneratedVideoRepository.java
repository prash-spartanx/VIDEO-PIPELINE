package com.prashant.pib.video_synthesis_service.repository;

import com.prashant.pib.video_synthesis_service.entity.GeneratedVideo;
import com.prashant.pib.video_synthesis_service.entity.VideoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GeneratedVideoRepository extends JpaRepository<GeneratedVideo, Long> {
    List<GeneratedVideo> findByUserId(Long userId);
    List<GeneratedVideo> findByPressReleaseId(Long pressReleaseId);

    /**
     * FLAW FIX: Replaced the untyped String-based method with a type-safe enum-based method.
     * This is the query the background poller will use to find all jobs
     * that are currently in the PROCESSING state.
     */
    List<GeneratedVideo> findByStatus(VideoStatus status);

    Optional<GeneratedVideo> findByJobId(String jobId);
}

