package com.prashant.pib.video_synthesis_service.repository;

import com.prashant.pib.video_synthesis_service.entity.PressRelease;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PressReleaseRepository extends JpaRepository<PressRelease, Long> {
    
    /**
     * Find press release by PRID (unique identifier from PIB)
     */
    Optional<PressRelease> findByPrid(String prid);
    
    /**
     * Check if press release exists by PRID
     */
    boolean existsByPrid(String prid);
    
    /**
     * Get top 10 most recent press releases
     */
    List<PressRelease> findTop10ByOrderByPublishedAtDesc();
    
    /**
     * Get all press releases for a specific user with sorting
     * This matches your original code structure
     */
    @Query("SELECT p FROM PressRelease p WHERE p.user.id = :userId")
    List<PressRelease> findByUserId(@Param("userId") Long userId, Sort sort);
    
    /**
     * Count press releases by user
     */
    @Query("SELECT COUNT(p) FROM PressRelease p WHERE p.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
    
    /**
     * Find press releases with empty content (for re-scraping)
     */
    @Query("SELECT p FROM PressRelease p WHERE p.content IS NULL OR p.content = '' ORDER BY p.publishedAt DESC")
    List<PressRelease> findPressReleasesWithEmptyContent();
    
    /**
     * Find press releases by language
     */
    List<PressRelease> findByLanguageOrderByPublishedAtDesc(String language);
}