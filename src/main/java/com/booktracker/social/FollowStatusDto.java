package com.booktracker.social;

/**
 * Response DTO for follow/unfollow operations.
 *
 * <p>A record because it is a simple data carrier with no lifecycle concerns.
 * Serializes to {@code {"following": true}} via Jackson.
 */
public record FollowStatusDto(boolean following) {
}
