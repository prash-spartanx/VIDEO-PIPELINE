package com.prashant.pib.video_synthesis_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

// DTO to map the initial job submission response from Python
@Data
@NoArgsConstructor
public class PythonJobResponse {
    private String message;
    private String job_id; // Snake case to match Python JSON
}