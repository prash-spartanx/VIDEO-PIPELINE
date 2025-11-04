package com.prashant.pib.video_synthesis_service.dto;

import lombok.Data;  // ✅ CRITICAL

@Data
public class PublishVideoRequest {
    private String publishedUrl;
    private String platform;
}