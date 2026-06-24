package com.booktracker.books;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

/**
 * RestClient configuration for the Open Library API.
 *
 * <p>Provides a named {@code openLibraryRestClient} bean configured with:
 * <ul>
 *   <li>Base URL from {@code openlibrary.base-url} (default: https://openlibrary.org)</li>
 *   <li>User-Agent header required for the Open Library 3 req/sec rate tier (BOOK-03)</li>
 *   <li>Connect and read timeouts from ISO-8601 duration properties</li>
 * </ul>
 *
 * <p>Startup validation: {@link #validateBaseUrl()} checks that {@code openlibrary.base-url}
 * resolves to a host ending with {@code openlibrary.org} — prevents SSRF via misconfiguration.
 *
 * <p>Separated from other config classes to follow the single-responsibility
 * pattern established by {@code PasswordEncoderConfig}.
 */
@Configuration
public class OpenLibraryConfig {

    @Value("${openlibrary.base-url:https://openlibrary.org}")
    private String baseUrl;

    @Value("${openlibrary.connect-timeout:PT5S}")
    private Duration connectTimeout;

    @Value("${openlibrary.read-timeout:PT10S}")
    private Duration readTimeout;

    @Value("${openlibrary.user-agent:BookTracker/1.0 (contact@example.com)}")
    private String userAgent;

    /**
     * Set to {@code false} in tests where WireMock overrides base-url to localhost.
     * Defaults to {@code true} — should never be set false in production configuration.
     */
    @Value("${openlibrary.validate-base-url:true}")
    private boolean validateBaseUrlEnabled;

    /**
     * Validates that {@code openlibrary.base-url} points to {@code openlibrary.org}.
     *
     * <p>Prevents SSRF via misconfiguration (WR-05): a misconfigured base-url pointing
     * at an internal endpoint (e.g. GCP metadata service) would cause all Open Library
     * calls to hit that endpoint instead, potentially leaking sensitive data.
     *
     * <p>Can be disabled for integration tests via {@code openlibrary.validate-base-url=false}
     * (WireMock overrides the base-url to localhost, which would otherwise fail this check).
     *
     * @throws IllegalStateException if the URL host does not end with {@code openlibrary.org}
     */
    @PostConstruct
    void validateBaseUrl() {
        if (!validateBaseUrlEnabled) {
            return;
        }
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            if (host == null || !host.endsWith("openlibrary.org")) {
                throw new IllegalStateException(
                        "openlibrary.base-url must point to openlibrary.org, got: " + baseUrl);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("openlibrary.base-url is not a valid URI: " + baseUrl, e);
        }
    }

    /**
     * RestClient bean for Open Library API calls.
     *
     * <p>Uses {@code ClientHttpRequestFactorySettings} (Spring Boot 3.2+ API)
     * to apply connect and read timeouts to the underlying HTTP factory.
     * The {@code RestClient.Builder} is auto-configured by Spring Boot and
     * inherits any registered customizers.
     *
     * @param builder auto-configured RestClient.Builder from Spring Boot
     * @return configured RestClient for Open Library
     */
    @Bean
    public RestClient openLibraryRestClient(RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);

        var requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return builder
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent)
                .requestFactory(requestFactory)
                .build();
    }
}
