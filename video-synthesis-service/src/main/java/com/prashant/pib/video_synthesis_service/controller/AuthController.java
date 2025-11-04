package com.prashant.pib.video_synthesis_service.controller;

import com.prashant.pib.video_synthesis_service.dto.AuthRequest;
import com.prashant.pib.video_synthesis_service.dto.AuthResponse;
import com.prashant.pib.video_synthesis_service.dto.RegisterRequest;
import com.prashant.pib.video_synthesis_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }
}