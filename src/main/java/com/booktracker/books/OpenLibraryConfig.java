package com.booktracker.books;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

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
                .defaultHeader("User-Agent", "BookTracker/1.0 (91katlego@gmail.com)")
                .requestFactory(requestFactory)
                .build();
    }
}
