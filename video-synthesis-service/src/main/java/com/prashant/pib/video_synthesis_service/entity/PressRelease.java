package com.prashant.pib.video_synthesis_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "press_releases",
    indexes = {
        @Index(name = "idx_press_releases_prid", columnList = "prid")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_press_releases_prid", columnNames = {"prid"})
    }
)
public class PressRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * PRIDs can be large or contain non-numeric chars in the future.
     * Store as VARCHAR and keep it unique.
     */
    @Column(name = "prid", nullable = false, length = 64, unique=true)
    private String prid;

    @Column(name = "title", length = 1024)
    private String title;

    @Column(name = "link", length = 1024)
    private String link;

    @Column(name = "language", length = 16)
    private String language;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",
            foreignKey = @ForeignKey(name = "fk_press_releases_user"))
    private User user;
}
