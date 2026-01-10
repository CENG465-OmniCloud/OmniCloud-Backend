package com.omnicloud.api.config;

import com.omnicloud.api.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for stateless APIs
                .authorizeHttpRequests(auth -> auth
                        // 1. PUBLIC ENDPOINTS (Register/Login/Swagger)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // 2. ADMIN ONLY ENDPOINTS (Infrastructure & Governance)
                        .requestMatchers("/api/v1/providers/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/policies/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/maintenance/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/analytics/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/audit/**").hasRole("ADMIN")

                        // 3. SHARED ENDPOINTS (File Operations)
                        // Both Users and Admins can upload/download files
                        .requestMatchers("/api/v1/files/**").hasAnyRole("USER", "ADMIN")

                        // 4. CATCH-ALL
                        .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}