package com.prashant.pib.video_synthesis_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "press_releases", uniqueConstraints = {
        @UniqueConstraint(columnNames = "prid")
})
@Data
public class PressRelease {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String prid;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column
    private String link;

    @Column
    private String language = "en";

    @Column
    private LocalDateTime publishedAt;

    // User relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column
    @CreationTimestamp  // Auto-set on persist (replaces manual = now())
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "pressRelease", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GeneratedVideo> generatedVideos = new ArrayList<>();
}