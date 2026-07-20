package com.booktracker.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the Cloudinary SDK bean (Phase 17, D-02).
 *
 * <p>Creates a single {@link Cloudinary} instance from environment-injected credentials.
 * The three required environment variables are:
 * <ul>
 *   <li>{@code CLOUDINARY_CLOUD_NAME} — Cloudinary account cloud name</li>
 *   <li>{@code CLOUDINARY_API_KEY}    — Cloudinary API key</li>
 *   <li>{@code CLOUDINARY_API_SECRET} — Cloudinary API secret</li>
 * </ul>
 *
 * <p><strong>T-17-06 (Information Disclosure):</strong> Credentials are injected via
 * {@code @Value} from environment variables only — never committed, never returned in
 * any API response. The {@code secure: true} flag ensures all Cloudinary URLs use HTTPS.
 *
 * <p>Application startup will fail if any of the three env vars is absent — this is
 * intentional; the human setup gate (Wave 0 checkpoint) must be completed first.
 */
@Configuration
public class CloudinaryConfig {

    /**
     * Produces a singleton {@link Cloudinary} bean configured from environment variables.
     *
     * @param cloudName Cloudinary cloud name (from {@code CLOUDINARY_CLOUD_NAME})
     * @param apiKey    Cloudinary API key (from {@code CLOUDINARY_API_KEY})
     * @param apiSecret Cloudinary API secret (from {@code CLOUDINARY_API_SECRET})
     * @return configured Cloudinary instance with {@code secure=true}
     */
    @Bean
    public Cloudinary cloudinary(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret,
                "secure",     true));  // always use HTTPS (returns secure_url)
    }
}
