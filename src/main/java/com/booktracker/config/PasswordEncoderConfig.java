package com.booktracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Standalone {@link PasswordEncoder} configuration — intentionally separated from
 * {@link SecurityConfig} to break the circular bean dependency:
 *
 * <pre>
 *   SecurityConfig → UserDetailsService → PasswordEncoder → SecurityConfig  (cycle!)
 * </pre>
 *
 * <p>By defining the {@code PasswordEncoder @Bean} here, Spring can satisfy the
 * {@code UserDetailsService} (via {@code AuthService}) dependency on {@code PasswordEncoder}
 * without going through {@code SecurityConfig}, eliminating the cycle
 * (RESEARCH.md Pitfall 3 / CLAUDE.md §Spring Security 6).
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt password encoder with default work factor (10).
     * Used by {@code AuthService} to hash plaintext passwords before persisting,
     * and by Spring Security's {@code DaoAuthenticationProvider} for credential verification.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
