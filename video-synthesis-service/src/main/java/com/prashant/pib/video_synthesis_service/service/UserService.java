package com.prashant.pib.video_synthesis_service.service;

import com.prashant.pib.video_synthesis_service.dto.AuthRequest;
import com.prashant.pib.video_synthesis_service.dto.AuthResponse;
import com.prashant.pib.video_synthesis_service.dto.RegisterRequest;

public interface UserService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(AuthRequest request);
}