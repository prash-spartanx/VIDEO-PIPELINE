package com.prashant.pib.video_synthesis_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data  // ✅ CRITICAL: Generates getters/setters/equals/hashCode/toString
@NoArgsConstructor  // ✅ CRITICAL: Default constructor
@AllArgsConstructor  // ✅ CRITICAL: All-args constructor (PUBLIC)
@Builder  // ✅ CRITICAL: Builder pattern for easy object creation
public class GeneratedVideoResponse {  // ✅ Class is public by default

    private Long id;
    private String videoUrl;
    private String status;
    private String publishedUrl;
    private String platform;
    private String language;
    private Long pressReleaseId;
    private String username;
    // ✅ Explicit Jackson annotations for LocalDateTime
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime publishedAt;
    // The jobId is returned immediately to the client for status polling.
    private String jobId;

    // The errorMessage is populated if the video generation fails.
    private String errorMessage;
}
/*
package com.prashant.pib.video_synthesis_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedVideoResponse {

    private Long id;
    private String videoUrl;
    private String status;
    private String publishedUrl;
    private String platform;
    private String language;
    private Long pressReleaseId;
    private String username;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;

    // --- FIX: These fields are required for the asynchronous job pattern ---
    // The jobId is returned immediately to the client for status polling.
    private String jobId;

    // The errorMessage is populated if the video generation fails.
    private String errorMessage;
}

 */