package com.prashant.pib.video_synthesis_service.config;

import com.prashant.pib.video_synthesis_service.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // --- CORS FIX --- Import HttpMethod
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration; // --- CORS FIX ---
import org.springframework.web.cors.CorsConfigurationSource; // --- CORS FIX ---
import org.springframework.web.cors.UrlBasedCorsConfigurationSource; // --- CORS FIX ---

import java.util.List; // --- CORS FIX ---

import static org.springframework.security.config.Customizer.withDefaults; // --- CORS FIX ---

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(UserDetailsService userDetailsService,
                          JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // --- CORS FIX ---
    // This bean defines the CORS rules for your application
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // This allows requests from any origin.
        // For production, you should restrict this to your frontend's domain:
        // configuration.setAllowedOrigins(List.of("https://your-frontend-domain.com"));
        configuration.setAllowedOrigins(List.of("*"));

        // Allow all standard HTTP methods
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Allow necessary headers like Authorization (for JWT) and Content-Type
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply these rules to all API paths
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // --- CORS FIX ---
                // Apply the CORS configuration defined in the bean above
                .cors(withDefaults())

                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // --- CORS FIX ---
                        // Explicitly allow all preflight OPTIONS requests to pass authentication

                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        
        // Static resources
                    .requestMatchers("/**.css", "/**.js", "/**.html", "/**.png", "/**.jpg", "/**.jpeg", "/**.gif", "/**.ico", "/**.svg").permitAll()
                    .requestMatchers("/").permitAll()
                    .requestMatchers("/assets/**").permitAll()  // NEW - Allow assets folder
                    .requestMatchers("/**.mp4").permitAll()     // NEW - Allow video files
        


                        // Your existing rules
                        .requestMatchers("/health", "/actuator/health").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}