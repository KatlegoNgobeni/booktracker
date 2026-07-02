-- V2__feed_performance_index.sql
-- Add covering index for the activity feed query:
-- "Give me all READ user_books for a given user ordered by date_finished DESC"
-- Extends the existing user_books_user_status_idx pattern to include date_finished.
-- Allows the feed subquery to use an index scan per followee, then merge-sort the streams.
-- This migration is additive only — no changes to the follows table (already complete in V1).
create index user_books_user_status_finished_idx
    on user_books (user_id, shelf_status, date_finished desc nulls last);
