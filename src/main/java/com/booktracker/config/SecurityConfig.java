package com.booktracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security filter chain configuration.
 *
 * <p>This plan (02-01) adds {@code /auth/register} to the permit list.
 * The JWT filter, STATELESS session policy, and {@code authenticationProvider}
 * are added in plan 02-02 (login + protected-endpoint slice) to keep this slice
 * scoped to the register flow only.
 *
 * <p><strong>Path note (D-08):</strong> requestMatchers use paths WITHOUT the
 * {@code /api} context-path prefix. Spring Security evaluates paths after the
 * servlet container strips the context-path. The existing {@code /health} path
 * confirms this convention.
 *
 * <p><strong>PasswordEncoder:</strong> defined in {@link PasswordEncoderConfig}
 * (not here) to avoid the SecurityConfig → UserDetailsService → PasswordEncoder
 * → SecurityConfig circular dependency (RESEARCH.md Pitfall 3).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // D-08: paths without /api prefix (context-path is stripped by container)
                .requestMatchers("/health").permitAll()
                .requestMatchers("/auth/register").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
