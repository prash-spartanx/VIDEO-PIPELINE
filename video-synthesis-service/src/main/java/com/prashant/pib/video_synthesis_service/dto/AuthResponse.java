package com.prashant.pib.video_synthesis_service.dto;

import lombok.Data;
import java.util.List; // <-- IMPORT THIS

@Data
public class AuthResponse {
    private String token;
    private String username;
    private List<String> roles; // <-- ADD THIS LINE
}