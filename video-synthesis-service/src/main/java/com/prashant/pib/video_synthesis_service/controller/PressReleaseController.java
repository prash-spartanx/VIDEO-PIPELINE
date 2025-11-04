package com.prashant.pib.video_synthesis_service.controller;

import com.prashant.pib.video_synthesis_service.dto.PressReleaseRequest;
import com.prashant.pib.video_synthesis_service.dto.PressReleaseResponse;
import com.prashant.pib.video_synthesis_service.service.PressReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/press-releases")
@RequiredArgsConstructor
// --- FIX: Security annotation moved to the class level for consistency and safety.
// --- This protects all endpoints in this controller.
@PreAuthorize("hasRole('ADMIN')")
public class PressReleaseController {

    private final PressReleaseService pressReleaseService;

    /**
     * Creates a new press release.
     * Accessible only by users with the 'ADMIN' role.
     */
    @PostMapping
    public ResponseEntity<PressReleaseResponse> createPressRelease(
            @RequestBody PressReleaseRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        PressReleaseResponse response = pressReleaseService.createPressRelease(request, username);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a single press release by its ID.
     * Accessible only by users with the 'ADMIN' role.
     * This endpoint fixes the mapping that was missing for your API call.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PressReleaseResponse> getPressRelease(@PathVariable Long id) {
        PressReleaseResponse response = pressReleaseService.getPressRelease(id);
        return ResponseEntity.ok(response);
    }

    /**
     * --- NEW: Added endpoint to fetch all press releases. ---
     * This functionality existed in the service but was not exposed via the API.
     * Accessible only by users with the 'ADMIN' role.
     */
    @GetMapping
    public ResponseEntity<List<PressReleaseResponse>> getAllPressReleases() {
        List<PressReleaseResponse> responses = pressReleaseService.getAllPressReleases();
        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves all press releases created by the currently authenticated admin user.
     * Accessible only by users with the 'ADMIN' role.
     */
    @GetMapping("/user")
    public ResponseEntity<List<PressReleaseResponse>> getPressReleasesByUser(Authentication authentication) {
        String username = authentication.getName();
        List<PressReleaseResponse> responses = pressReleaseService.getPressReleasesByUser(username);
        return ResponseEntity.ok(responses);
    }
}

