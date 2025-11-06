package com.prashant.pib.video_synthesis_service.repository;

import com.prashant.pib.video_synthesis_service.entity.PressRelease;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PressReleaseRepository extends JpaRepository<PressRelease, Long> {
    Optional<PressRelease> findByPrid(String prid);
    boolean existsByPrid(String prid);

    // you already use this in the service
    java.util.List<PressRelease> findByUserId(Long userId, Sort sort);
}
