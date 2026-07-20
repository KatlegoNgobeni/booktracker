-- V10__profile_photo.sql
-- Add nullable profile_photo_url column to users table.
-- NULL = no photo set (Dicebear fallback on client per D-11).
-- Cloudinary HTTPS secure_url stored when photo is uploaded (D-04).
-- No NOT NULL constraint: existing users start with NULL and receive
-- the Dicebear-generated avatar on all identity surfaces.
ALTER TABLE users ADD COLUMN profile_photo_url varchar;
