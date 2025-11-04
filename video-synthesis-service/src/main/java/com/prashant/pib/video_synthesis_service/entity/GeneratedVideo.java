package com.prashant.pib.video_synthesis_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "generated_videos")
@Data
public class GeneratedVideo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- FIX: This is only known on completion, so it MUST be nullable.
    @Column(nullable = true)
    private String videoUrl;

    // --- FLAW FIX: Replaced insecure String with a type-safe Enum.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoStatus status;

    // --- FIX: Added field to track the Python job. This is the key to traceability.
    @Column(unique = true)
    private String jobId;

    // --- FIX: Added field to store failure reasons.
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    private String publishedUrl;

    @Column
    private String platform;

    @Column
    private String language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "press_release_id", nullable = false)
    private PressRelease pressRelease;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
