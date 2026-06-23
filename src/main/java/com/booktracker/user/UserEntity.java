package com.booktracker.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for the {@code users} table — also implements Spring Security's
 * {@code UserDetails} to eliminate the need for a separate principal wrapper.
 *
 * <p>Column mapping (from V1__initial_schema.sql):
 * <ul>
 *   <li>{@code id}            uuid PK   → {@link UUID} via {@code GenerationType.UUID} (Hibernate 6)</li>
 *   <li>{@code email}         varchar   → {@code String email}</li>
 *   <li>{@code password_hash} varchar   → {@code String passwordHash} (@Column name explicit)</li>
 *   <li>{@code display_name}  varchar   → {@code String displayName} (@Column name explicit)</li>
 *   <li>{@code created_at}    timestamptz → {@code OffsetDateTime createdAt} (immutable after insert)</li>
 * </ul>
 *
 * <p><strong>Namespace:</strong> {@code jakarta.persistence.*} — Spring Boot 3.x requires Jakarta EE,
 * not {@code javax.persistence.*} (CLAUDE.md §Version Gotchas).
 *
 * <p><strong>UUID strategy:</strong> {@code GenerationType.UUID} (Hibernate 6).
 * The {@code "uuid2"} strategy from Hibernate 5 is REMOVED and must not be used.
 *
 * <p><strong>UserDetails contract (D-06):</strong>
 * {@code getUsername()} returns {@code id.toString()} — the UUID string used as the
 * JWT {@code sub} claim. This is the key used by {@code UserDetailsService.loadUserByUsername()}.
 */
@Entity
@Table(name = "users")
public class UserEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Maps to the {@code password_hash} column — stored as a BCrypt hash, never plaintext. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** Immutable — defaulted at insert time via {@code @PrePersist}. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Sets {@code createdAt} before the first persist if not already set. */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // ----------------------------------------------------------------
    // UserDetails contract
    // ----------------------------------------------------------------

    /** Returns the user's UUID string — used as the JWT {@code sub} claim (D-06). */
    @Override
    public String getUsername() {
        return id.toString();
    }

    /** Returns the BCrypt-hashed password stored in {@code password_hash}. */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** No roles in Phase 2 MVP — returns an empty collection. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // ----------------------------------------------------------------
    // Getters and setters
    // ----------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
