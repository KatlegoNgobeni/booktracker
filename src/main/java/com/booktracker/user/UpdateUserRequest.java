package com.booktracker.user;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/users/me}.
 *
 * <p>All fields are optional — only non-null fields are applied.
 * Password change requires {@code currentPassword} to be provided alongside {@code newPassword}.
 */
public class UpdateUserRequest {

    @Size(min = 1, max = 50, message = "Display name must be 1–50 characters")
    private String displayName;

    private String currentPassword;

    @Size(min = 8, message = "New password must be at least 8 characters")
    private String newPassword;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
