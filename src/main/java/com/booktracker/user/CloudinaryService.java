package com.booktracker.user;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Service for Cloudinary profile photo upload and deletion (Phase 17, D-02/D-04).
 *
 * <p>Encapsulates all Cloudinary SDK calls so that {@link UserService} remains
 * free of CDN-specific logic. Constructor injection of {@link Cloudinary} bean
 * (from {@link com.booktracker.config.CloudinaryConfig}) ensures credentials
 * never pass through this service — they are baked into the bean at startup.
 *
 * <p><strong>T-17-03 (SSRF via public_id injection):</strong> The {@code public_id}
 * is constructed server-side as {@code "profile_photos/" + userId.toString()} — no
 * user-supplied input reaches the Cloudinary public_id field.
 *
 * <p><strong>D-04 (face-centered crop):</strong> Upload params apply an inline
 * incoming transformation: {@code crop=fill, gravity=face, width=400, height=400}.
 * With inline params (not an {@code eager} array), Cloudinary applies the transformation
 * to the stored asset itself. The returned {@code secure_url} is the URL of the
 * transformed (cropped) image — store this URL directly in the DB.
 *
 * <p><strong>Overwrite strategy:</strong> {@code overwrite=true} combined with a
 * deterministic {@code public_id} based on userId means re-uploading always replaces
 * the previous photo in the same Cloudinary slot. No explicit delete is needed on re-upload.
 */
@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Uploads image bytes to Cloudinary with face-centered auto-crop (D-04).
     *
     * <p>Transformation applied inline (incoming transformation — not eager):
     * {@code crop=fill + gravity=face} at 400x400 produces a face-centered square.
     * Store the returned {@code secure_url} directly in the DB (D-04).
     *
     * <p><strong>T-17-03:</strong> {@code public_id} is server-constructed from
     * the authenticated userId — no user input in the path.
     *
     * @param imageBytes validated image bytes (magic-byte check already passed in controller)
     * @param userId     the authenticated user's UUID (from {@code @AuthenticationPrincipal})
     * @return Cloudinary {@code secure_url} of the face-cropped 400x400 image
     * @throws IOException if the Cloudinary upload fails (caller maps to 502 Bad Gateway)
     */
    @SuppressWarnings("unchecked")
    public String uploadProfilePhoto(byte[] imageBytes, UUID userId) throws IOException {
        Map<String, Object> params = ObjectUtils.asMap(
                "public_id",     "profile_photos/" + userId.toString(),
                "overwrite",     true,
                "resource_type", "image",
                "width",         400,
                "height",        400,
                "crop",          "fill",
                "gravity",       "face"
        );
        Map result = cloudinary.uploader().upload(imageBytes, params);
        return (String) result.get("secure_url");
    }

    /**
     * Deletes the profile photo asset from Cloudinary.
     *
     * <p>Called by {@code UserService.removeProfilePhoto()} before setting
     * {@code profile_photo_url = null} in the DB.
     *
     * <p><strong>T-17-03:</strong> {@code public_id} is server-constructed — no user input.
     *
     * @param userId the authenticated user's UUID
     * @throws Exception if the Cloudinary destroy call fails
     */
    public void deleteProfilePhoto(UUID userId) throws Exception {
        cloudinary.uploader().destroy(
                "profile_photos/" + userId.toString(),
                ObjectUtils.emptyMap());
    }
}
