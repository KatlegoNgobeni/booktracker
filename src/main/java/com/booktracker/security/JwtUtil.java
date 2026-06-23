package com.booktracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT utility — generates signed JWTs and validates/parses them using jjwt 0.12.x.
 *
 * <p><strong>API note:</strong> This class uses ONLY the jjwt 0.12.x fluent API.
 * The following 0.11.x methods are REMOVED and must NOT appear here:
 * {@code parserBuilder()}, {@code parseClaimsJws()}, {@code getBody()},
 * {@code setSubject()}, {@code setIssuedAt()}, {@code setExpiration()},
 * {@code signWith(key, SignatureAlgorithm)}.
 *
 * <p>Claims (D-06): only {@code sub} (user UUID string), {@code iat}, {@code exp}.
 * Email and displayName are never embedded — fetched from DB per request via UserDetailsService.
 *
 * <p>The signing key is derived by base64-decoding {@code JWT_SECRET} env var.
 * The decoded bytes must be ≥32 bytes for HMAC-SHA256 (D-07).
 */
@Component
public class JwtUtil {

    /** 24-hour token expiry in milliseconds (D-02). */
    private static final long EXPIRATION_MS = 24L * 60 * 60 * 1000;

    /**
     * JWT signing secret — loaded from the {@code JWT_SECRET} environment variable
     * via {@code application.properties} property {@code jwt.secret=${JWT_SECRET}}.
     * Must be a base64-encoded value that decodes to ≥32 bytes.
     */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Package-private setter for test injection — avoids the need for a Spring
     * context or reflection in unit tests (see {@code JwtUtilTest}).
     */
    void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    /**
     * Generates a signed JWT with the user's UUID as the {@code sub} claim.
     *
     * @param userUuid the user's UUID string (from {@code UserEntity.getId().toString()})
     * @return compact JWS string
     */
    public String generateToken(String userUuid) {
        return Jwts.builder()
                .subject(userUuid)                                                         // D-06: sub = UUID
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))         // D-02: 24h
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts the {@code sub} claim (user UUID string) from a valid signed JWT.
     *
     * @param token the compact JWS string
     * @return the UUID string stored in the {@code sub} claim
     * @throws io.jsonwebtoken.JwtException if the token is invalid, expired, or tampered
     */
    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Returns {@code true} if the token's expiration time is in the past.
     *
     * @param token the compact JWS string
     * @return {@code true} if expired; {@code false} otherwise
     */
    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    /**
     * Derives the HMAC-SHA signing key by base64-decoding the injected secret.
     * jjwt infers the HMAC algorithm from the key length (≥32 bytes → HS256).
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Parses and verifies the JWT, returning all claims.
     *
     * <p>Uses the jjwt 0.12.x API exclusively:
     * {@code Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()}
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
