package com.prashant.pib.video_synthesis_service.service;

import com.prashant.pib.video_synthesis_service.dto.AuthRequest;
import com.prashant.pib.video_synthesis_service.dto.AuthResponse;
import com.prashant.pib.video_synthesis_service.dto.RegisterRequest;
import com.prashant.pib.video_synthesis_service.entity.User;
import com.prashant.pib.video_synthesis_service.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority; // --- IMPORT ADDED ---
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List; // --- IMPORT ADDED ---
import java.util.stream.Collectors; // --- IMPORT ADDED ---

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Override
    public AuthResponse register(RegisterRequest request) {
        // Check if user already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Ensure role is set, default to "USER"
        String requestedRole = request.getRole() != null ? request.getRole() : "USER";
        // Remove "ROLE_" prefix if user accidentally added it, as we store the clean role
        requestedRole = requestedRole.startsWith("ROLE_") ? requestedRole.substring(5) : requestedRole;
        user.setRole(requestedRole);

        user = userRepository.save(user);

        String token = generateJwtToken(user.getUsername());
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUsername(user.getUsername());

        // --- FIX ---
        // We add the "ROLE_" prefix here for the response, matching what
        // Spring Security's "login" method will provide.
        // This ensures script.js works immediately after registering.
        String roleForResponse = "ROLE_" + user.getRole();
        response.setRoles(List.of(roleForResponse));

        return response;
    }

    @Override
    public AuthResponse login(AuthRequest request) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // --- FIX ---
        // Extract the user's roles (e.g., "ROLE_ADMIN") from the
        // successfully authenticated 'Authentication' object.
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        String token = generateJwtToken(request.getUsername());
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUsername(request.getUsername());

        // --- FIX ---
        // Set the roles in the response. script.js will now receive this.
        response.setRoles(roles);

        return response;
    }

    private String generateJwtToken(String username) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(key)
                .compact();
    }
}
