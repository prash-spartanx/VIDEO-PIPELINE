package com.prashant.pib.video_synthesis_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

// DTO to map the status check response from Python
@Data
@NoArgsConstructor
public class PythonStatusResponse {
    private String status;
    private String message;
    private String video_url; // Snake case
}