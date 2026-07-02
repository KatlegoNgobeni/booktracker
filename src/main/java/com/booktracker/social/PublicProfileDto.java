package com.booktracker.social;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.domain.Page;

/**
 * DTO for the public profile response ({@code GET /api/users/{id}/profile}).
 *
 * <p>Uses {@code @JsonInclude(NON_NULL)} so that {@code goalTarget} and
 * {@code goalProgressPercent} are omitted from the JSON response when the user has
 * not set a reading goal (null = no goal set).
 *
 * <p>This is a class (not a record) because:
 * <ul>
 *   <li>It contains a {@link Page}{@code <PublicShelfEntryDto>} field, which has complex
 *       generic serialization requirements that are handled better via explicit getters.</li>
 *   <li>{@code @JsonInclude} on individual fields requires non-record semantics.</li>
 * </ul>
 *
 * <p>The {@code isFollowing} getter is named {@code isFollowing()} (boolean convention)
 * so Jackson serializes it as {@code "isFollowing"} in the JSON output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicProfileDto {

    private String userId;
    private String displayName;
    private long followerCount;
    private long followingCount;
    private boolean isFollowing;
    private Integer goalTarget;
    private Double goalProgressPercent;
    private long booksReadThisYear;
    private Page<PublicShelfEntryDto> readEntries;

    public PublicProfileDto(
            String userId,
            String displayName,
            long followerCount,
            long followingCount,
            boolean isFollowing,
            Integer goalTarget,
            Double goalProgressPercent,
            long booksReadThisYear,
            Page<PublicShelfEntryDto> readEntries) {
        this.userId = userId;
        this.displayName = displayName;
        this.followerCount = followerCount;
        this.followingCount = followingCount;
        this.isFollowing = isFollowing;
        this.goalTarget = goalTarget;
        this.goalProgressPercent = goalProgressPercent;
        this.booksReadThisYear = booksReadThisYear;
        this.readEntries = readEntries;
    }

    // ----------------------------------------------------------------
    // Getters
    // ----------------------------------------------------------------

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getFollowerCount() {
        return followerCount;
    }

    public long getFollowingCount() {
        return followingCount;
    }

    public boolean isFollowing() {
        return isFollowing;
    }

    public Integer getGoalTarget() {
        return goalTarget;
    }

    public Double getGoalProgressPercent() {
        return goalProgressPercent;
    }

    public long getBooksReadThisYear() {
        return booksReadThisYear;
    }

    public Page<PublicShelfEntryDto> getReadEntries() {
        return readEntries;
    }
}
