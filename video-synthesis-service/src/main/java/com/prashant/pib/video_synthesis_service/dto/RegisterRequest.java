package com.prashant.pib.video_synthesis_service.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private String role;  // Optional, defaults to "USER"
}