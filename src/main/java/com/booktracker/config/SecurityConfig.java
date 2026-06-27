package com.booktracker.config;

import com.booktracker.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security filter chain configuration for the JWT stateless auth layer.
 *
 * <p><strong>02-02 additions over 02-01 stub:</strong>
 * <ul>
 *   <li>{@code SessionCreationPolicy.STATELESS} — no HTTP sessions, no JSESSIONID (Pitfall 4, T-02-10)</li>
 *   <li>{@code /auth/login} added to permit list (D-08)</li>
 *   <li>{@code DaoAuthenticationProvider @Bean} (authenticationProvider) for credential checks</li>
 *   <li>{@code JwtAuthenticationFilter} inserted before {@code UsernamePasswordAuthenticationFilter} (D-09)</li>
 *   <li>{@code AuthenticationManager @Bean} exposed for use by {@code AuthService.login}</li>
 * </ul>
 *
 * <p><strong>Path note (07-01 refactor):</strong> context-path removed from application.properties;
 * all controllers now declare {@code /api} in their own {@code @RequestMapping}. Security
 * matchers therefore use full paths (e.g., {@code /api/auth/register}) — no context-path stripping.
 *
 * <p><strong>PasswordEncoder note (Pitfall 3):</strong> The {@code PasswordEncoder} bean is
 * NOT defined here — it lives in {@link PasswordEncoderConfig} to avoid the
 * SecurityConfig → UserDetailsService → PasswordEncoder → SecurityConfig circular dependency.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructor injection keeps all dependencies explicit and avoids field-injection
     * issues with circular reference detection.
     *
     * @param jwtAuthenticationFilter JWT filter bean (from security package)
     * @param userDetailsService      UserService — primary UserDetailsService bean (UUID-based)
     * @param passwordEncoder         BCryptPasswordEncoder from PasswordEncoderConfig
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          UserDetailsService userDetailsService,
                          PasswordEncoder passwordEncoder) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Configures the security filter chain:
     * <ul>
     *   <li>CSRF disabled — stateless API, no browser form submissions</li>
     *   <li>STATELESS session — no server-side session state (T-02-10, Pitfall 4)</li>
     *   <li>3-rule pattern (07-01): bootstrap API endpoints permitAll; all /api/** authenticated;
     *       everything else (SPA shell + assets) permitAll (T-07-01, T-07-03)</li>
     *   <li>JWT filter before UsernamePasswordAuthenticationFilter (D-09)</li>
     *   <li>DaoAuthenticationProvider registered for credential-based authentication</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // Pitfall 4 / T-02-10: STATELESS — no JSESSIONID, no server-side session
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // T-02-09 / T-07-01: allow register, login, and health unauthenticated
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/health").permitAll()
                // T-07-01: gate all other /api/** paths behind JWT
                .requestMatchers("/api/**").authenticated()
                // Allow Spring Boot's /error endpoint so that ResponseStatusException
                // (e.g. Open Library 404 → our 404) is forwarded to the error handler
                // without Spring Security blocking the error dispatch with 401.
                .requestMatchers("/error").permitAll()
                // T-07-03: SPA shell (index.html) and static assets are intentionally public;
                // all sensitive data access still requires a JWT at /api/**
                .anyRequest().permitAll()
            )
            // AUTH-04: Return 401 (not 403) when no valid JWT is present.
            // Spring Security 6 defaults to 403 without an AuthenticationEntryPoint;
            // this entry point ensures unauthenticated requests get 401 as required.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
            // DaoAuthenticationProvider for credential-based login
            .authenticationProvider(authenticationProvider())
            // D-09: JWT filter placed before UsernamePasswordAuthenticationFilter
            // so that valid tokens populate SecurityContext before authorization checks
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * DaoAuthenticationProvider — delegates credential verification to UserDetailsService
     * (UUID-based loading) and BCryptPasswordEncoder (password comparison).
     *
     * <p>Note: this provider is registered in the SecurityFilterChain but NOT directly
     * used by {@code AuthService.login} — that method does its own email lookup +
     * BCrypt comparison to keep the UserDetailsService UUID-based for the filter pipeline.
     *
     * <p>This bean is required by Spring Security to support programmatic authentication
     * via the {@code AuthenticationManager} if needed in future.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        // PasswordEncoder injected from PasswordEncoderConfig — NOT defined here (Pitfall 3)
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Exposes the {@code AuthenticationManager} as a bean for injection into service classes.
     *
     * <p>Built from the current {@code AuthenticationConfiguration} — ensures it uses
     * the same providers configured in this SecurityConfig.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
