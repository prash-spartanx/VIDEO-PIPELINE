package com.prashant.pib.video_synthesis_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO for the request body sent to Python
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PythonVideoRequest {
    private String content;
    private String language;
}