-- V6__abandoned_shelf_status.sql
-- Whitelists the four valid ShelfStatus values on user_books.shelf_status.
-- ABANDONED is new in Phase 10 (DNF shelf status). The three original values
-- (WANT_TO_READ, CURRENTLY_READING, READ) were permitted without a CHECK constraint
-- since V1. This migration adds the constraint additively — no data backfill needed.
-- This migration is immutable once applied.

alter table user_books
    add constraint user_books_shelf_status_chk
        check (shelf_status in ('WANT_TO_READ', 'CURRENTLY_READING', 'READ', 'ABANDONED'));
