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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
            // be forgiving but never store null title (DB not-null)
            if (request.getContent() != null && !request.getContent().isBlank()) {
                String auto = autoTitleFromContent(request.getContent());
                request.setTitle(auto);
            } else {
                throw new IllegalArgumentException("Title cannot be empty");
            }
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        PressRelease pr = new PressRelease();
        pr.setPrid(
                request.getPrid() != null && !request.getPrid().isBlank()
                        ? request.getPrid()
                        : "manual-" + System.currentTimeMillis()
        );
        pr.setTitle(trimCap(request.getTitle(), 1024));
        pr.setContent(request.getContent());
        pr.setLink(request.getLink());
        pr.setLanguage(request.getLanguage() != null ? request.getLanguage() : "en");
        pr.setPublishedAt(request.getPublishedAt() != null ? request.getPublishedAt() : LocalDateTime.now());
        pr.setUser(user);

        PressRelease saved = pressReleaseRepository.save(pr);
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

        List<PressReleaseResponse> responses = pressReleaseRepository
                .findByUserId(user.getId(), Sort.by(Sort.Direction.DESC, "publishedAt"))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        log.info("✓ Fetched {} press releases for user {}", responses.size(), username);
        return responses;
    }

    /**
     * IMPORTANT: we skip invalid content and ensure title is non-null before persisting.
     */
    @Override
    @Transactional(noRollbackFor = Exception.class)
    public List<PressReleaseResponse> saveFetchedPressReleases(List<PibPressReleaseDto> dtos, String username) {
        if (dtos == null || dtos.isEmpty()) {
            log.warn("⚠ No DTOs to process for user {}", username);
            return List.of();
        }

        log.info("Processing {} fetched press releases for user {}", dtos.size(), username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        List<PressRelease> persisted = new ArrayList<>();
        int newCount = 0, updatedCount = 0, skippedCount = 0;

        for (PibPressReleaseDto dto : dtos) {
            try {
                if (dto == null) { skippedCount++; continue; }

                final String prid = safeTrim(dto.getPrid());
                if (prid == null || prid.isEmpty()) { skippedCount++; continue; }

                final String description = safeTrim(dto.getDescription());
                if (description == null || description.isEmpty() || !PibFetcherServiceImpl.ContentValidator.isLikelyValid(description)) {
                    log.warn("⚠ Skipping PRID {} — empty/blocked content", prid);
                    skippedCount++;
                    continue;
                }

                String incomingTitle = safeTrim(dto.getTitle());
                if (incomingTitle == null || incomingTitle.isBlank()) {
                    incomingTitle = autoTitleFromContent(description);
                }

                Optional<PressRelease> existingOpt = pressReleaseRepository.findByPrid(prid);
                if (existingOpt.isPresent()) {
                    PressRelease existing = existingOpt.get();
                    boolean changed = false;

                    if (isNullOrBlank(existing.getContent()) || existing.getContent().length() < description.length()) {
                        existing.setContent(description);
                        changed = true;
                    }
                    if (!incomingTitle.equals(existing.getTitle())) {
                        existing.setTitle(trimCap(incomingTitle, 1024));
                        changed = true;
                    }
                    String newLink = safeTrim(dto.getLink());
                    if (newLink != null && !newLink.equals(existing.getLink())) {
                        existing.setLink(newLink);
                        changed = true;
                    }
                    if (dto.getPublishedAt() != null && !dto.getPublishedAt().equals(existing.getPublishedAt())) {
                        existing.setPublishedAt(dto.getPublishedAt());
                        changed = true;
                    }
                    String newLang = safeTrim(dto.getLanguage());
                    if (newLang != null && !newLang.equals(existing.getLanguage())) {
                        existing.setLanguage(newLang);
                        changed = true;
                    }

                    if (changed) {
                        PressRelease saved = pressReleaseRepository.save(existing);
                        persisted.add(saved);
                        updatedCount++;
                        log.info("↻ Updated PRID {} ({} chars)", prid, description.length());
                    } else {
                        skippedCount++;
                        log.debug("⊘ Skipping PRID {} (no meaningful changes)", prid);
                    }
                } else {
                    PressRelease pr = new PressRelease();
                    pr.setPrid(prid);
                    pr.setTitle(trimCap(incomingTitle, 1024));
                    pr.setContent(description);
                    pr.setLink(safeTrim(dto.getLink()));
                    pr.setLanguage(safeOrDefault(dto.getLanguage(), "en"));
                    pr.setPublishedAt(dto.getPublishedAt() != null ? dto.getPublishedAt() : LocalDateTime.now());
                    pr.setUser(user);

                    PressRelease saved = pressReleaseRepository.save(pr);
                    persisted.add(saved);
                    newCount++;
                    log.info("+ Saved PRID {} ({} chars)", prid, description.length());
                }
            } catch (DataIntegrityViolationException dive) {
                skippedCount++;
                log.error("DB constraint error for PRID {}: {}", dto != null ? dto.getPrid() : "null", dive.getMessage());
            } catch (Exception e) {
                skippedCount++;
                log.error("Error processing PR DTO (PRID={}): {}", dto != null ? dto.getPrid() : "null", e.getMessage(), e);
            }
        }

        log.info("✅ Processed {} press releases: {} new, {} updated, {} skipped",
                dtos.size(), newCount, updatedCount, skippedCount);

        return persisted.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // ---------- helpers ----------

    private boolean isNullOrBlank(String s) {
        return s == null || s.isBlank();
    }

    private String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private String safeOrDefault(String s, String def) {
        String t = safeTrim(s);
        return (t == null || t.isEmpty()) ? def : t;
    }

    private String autoTitleFromContent(String content) {
        if (content == null) return "Press Release";
        String t = content.trim().replaceAll("\\s+", " ");
        if (t.length() > 150) t = t.substring(0, 150) + "…";
        return t.isEmpty() ? "Press Release" : t;
    }

    private String trimCap(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
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
