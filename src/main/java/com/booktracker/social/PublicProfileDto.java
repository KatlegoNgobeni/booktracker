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
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicProfileDto {

    private String userId;
    private String displayName;
    private long friendCount;
    private Integer goalTarget;
    private Double goalProgressPercent;
    private long booksReadThisYear;
    private Page<PublicShelfEntryDto> readEntries;

    public PublicProfileDto(
            String userId,
            String displayName,
            long friendCount,
            Integer goalTarget,
            Double goalProgressPercent,
            long booksReadThisYear,
            Page<PublicShelfEntryDto> readEntries) {
        this.userId = userId;
        this.displayName = displayName;
        this.friendCount = friendCount;
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

    public long getFriendCount() {
        return friendCount;
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
