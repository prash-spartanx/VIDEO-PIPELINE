package com.prashant.pib.video_synthesis_service.service;

import com.prashant.pib.video_synthesis_service.dto.PibPressReleaseDto;
import com.prashant.pib.video_synthesis_service.dto.PressReleaseRequest;
import com.prashant.pib.video_synthesis_service.dto.PressReleaseResponse;
import com.prashant.pib.video_synthesis_service.entity.PressRelease;
import com.prashant.pib.video_synthesis_service.entity.User;
import com.prashant.pib.video_synthesis_service.repository.PressReleaseRepository;
import com.prashant.pib.video_synthesis_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PressReleaseServiceImpl implements PressReleaseService {

    private final PressReleaseRepository pressReleaseRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public PressReleaseResponse createPressRelease(PressReleaseRequest request, String username) {
        log.info("Creating manual press release for user: {}", username);
        
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }
        
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        PressRelease pressRelease = new PressRelease();
        pressRelease.setPrid(
                request.getPrid() != null && !request.getPrid().isBlank() 
                    ? request.getPrid() 
                    : "manual-" + System.currentTimeMillis()
        );
        pressRelease.setTitle(request.getTitle());
        pressRelease.setContent(request.getContent());
        pressRelease.setLink(request.getLink());
        pressRelease.setLanguage(request.getLanguage() != null ? request.getLanguage() : "en");
        pressRelease.setPublishedAt(request.getPublishedAt() != null 
                ? request.getPublishedAt() 
                : java.time.LocalDateTime.now());
        pressRelease.setUser(user);

        PressRelease saved = pressReleaseRepository.save(pressRelease);
        log.info("✓ Created manual PRID {} for user {}", saved.getPrid(), username);

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PressReleaseResponse getPressRelease(Long id) {
        log.debug("Fetching press release with ID: {}", id);
        
        PressRelease pr = pressReleaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Press release not found with ID: " + id));

        if (pr.getContent() == null || pr.getContent().isBlank()) {
            log.warn("⚠ PRID {} has empty content — video generation may fail", pr.getPrid());
        }

        return mapToResponse(pr);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PressReleaseResponse> getAllPressReleases() {
        log.debug("Fetching all press releases");
        
        List<PressReleaseResponse> responses = pressReleaseRepository
                .findAll(Sort.by(Sort.Direction.DESC, "publishedAt"))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        
        log.info("✓ Fetched {} total press releases", responses.size());
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PressReleaseResponse> getPressReleasesByUser(String username) {
        log.debug("Fetching press releases for user: {}", username);
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // Use the existing repository method with Sort instead of Pageable
        List<PressReleaseResponse> responses = pressReleaseRepository
                .findByUserId(user.getId(), Sort.by(Sort.Direction.DESC, "publishedAt"))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        
        log.info("✓ Fetched {} press releases for user {}", responses.size(), username);
        return responses;
    }

    @Override
    @Transactional
    public List<PressReleaseResponse> saveFetchedPressReleases(List<PibPressReleaseDto> dtos, String username) {
        if (dtos == null || dtos.isEmpty()) {
            log.warn("⚠ No DTOs to process for user {}", username);
            return List.of();
        }

        log.info("Processing {} fetched press releases for user {}", dtos.size(), username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        List<PressRelease> savedPressReleases = new ArrayList<>();
        int newCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (PibPressReleaseDto dto : dtos) {
            try {
                Optional<PressRelease> result = processFetchedDto(dto, user);
                if (result.isPresent()) {
                    PressRelease pr = result.get();
                    savedPressReleases.add(pr);
                    
                    // Check if it was an update or new entry
                    if (pr.getId() != null && pressReleaseRepository.existsById(pr.getId())) {
                        updatedCount++;
                    } else {
                        newCount++;
                    }
                } else {
                    skippedCount++;
                }
            } catch (Exception e) {
                log.error("Error processing DTO for PRID {}: {}", dto.getPrid(), e.getMessage());
                skippedCount++;
            }
        }

        log.info("✅ Processed {} press releases: {} new, {} updated, {} skipped", 
                dtos.size(), newCount, updatedCount, skippedCount);

        return savedPressReleases.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private Optional<PressRelease> processFetchedDto(PibPressReleaseDto dto, User user) {
        if (dto.getPrid() == null || dto.getPrid().isBlank()) {
            log.warn("⚠ Skipping invalid DTO: missing PRID");
            return Optional.empty();
        }

        // Check if already exists
        Optional<PressRelease> existingOpt = pressReleaseRepository.findByPrid(dto.getPrid());
        
        if (existingOpt.isPresent()) {
            PressRelease existing = existingOpt.get();
            
            // Only update if existing has no content but new DTO has content
            if ((existing.getContent() == null || existing.getContent().isBlank())
                    && dto.getDescription() != null && !dto.getDescription().isBlank()) {
                
                existing.setTitle(dto.getTitle());
                existing.setContent(dto.getDescription());
                existing.setLink(dto.getLink());
                existing.setPublishedAt(dto.getPublishedAt());
                
                PressRelease updated = pressReleaseRepository.save(existing);
                log.info("↻ Updated PRID {} with new content ({} chars)", 
                        dto.getPrid(), dto.getDescription().length());
                return Optional.of(updated);
            }
            
            log.debug("⊘ Skipping existing PRID {} (already has valid content)", dto.getPrid());
            return Optional.empty();
        }

        // Validate new entry has content
        if (dto.getDescription() == null || dto.getDescription().isBlank()) {
            log.warn("⚠ Skipping PRID {} — scraping failed (empty content)", dto.getPrid());
            return Optional.empty();
        }

        // Create new press release
        PressRelease pr = new PressRelease();
        pr.setPrid(dto.getPrid());
        pr.setTitle(dto.getTitle());
        pr.setContent(dto.getDescription());
        pr.setLink(dto.getLink());
        pr.setLanguage(dto.getLanguage() != null ? dto.getLanguage() : "en");
        pr.setPublishedAt(dto.getPublishedAt() != null 
                ? dto.getPublishedAt() 
                : java.time.LocalDateTime.now());
        pr.setUser(user);
        
        PressRelease saved = pressReleaseRepository.save(pr);
        log.info("+ Saved new PRID {} ({} chars)", dto.getPrid(), dto.getDescription().length());
        return Optional.of(saved);
    }

    private PressReleaseResponse mapToResponse(PressRelease pr) {
        return PressReleaseResponse.builder()
                .id(pr.getId())
                .prid(pr.getPrid())
                .title(pr.getTitle())
                .content(pr.getContent())
                .link(pr.getLink())
                .language(pr.getLanguage())
                .publishedAt(pr.getPublishedAt())
                .username(pr.getUser() != null ? pr.getUser().getUsername() : null)
                .createdAt(pr.getCreatedAt())
                .build();
    }
}