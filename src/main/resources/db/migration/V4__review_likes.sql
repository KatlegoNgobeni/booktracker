-- V4__review_likes.sql
-- Review likes: one like per user per user_books entry (DISC-04).
-- V3 (friend_requests) is owned by Plan 09-01; this is the next sequential migration.
-- This migration is immutable once applied.

-- ============================================================
-- TABLE: review_likes
-- One like per (user_id, entry_id) pair — enforced by pair_uq constraint.
-- Immutable row: no updatedAt column — likes cannot be edited, only created/deleted.
-- ============================================================
create table review_likes (
    id         uuid        not null default gen_random_uuid(),
    user_id    uuid        not null,
    entry_id   uuid        not null,
    created_at timestamptz not null default now(),

    constraint review_likes_pkey     primary key (id),
    constraint review_likes_user_fk  foreign key (user_id)  references users(id)      on delete cascade,
    constraint review_likes_entry_fk foreign key (entry_id) references user_books(id) on delete cascade,
    constraint review_likes_pair_uq  unique (user_id, entry_id)
);

-- Index on entry_id for efficient COUNT + EXISTS queries when building PublicShelfEntryDto
create index review_likes_entry_idx on review_likes (entry_id);
