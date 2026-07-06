-- V5__notifications.sql
-- Persisted notifications for NOTIF-01 / NOTIF-03.
-- V3 (friend_requests) and V4 (review_likes) precede this migration.
-- This migration is immutable once applied.

-- ============================================================
-- TABLE: notifications
-- One row per notification event. is_read=false until the user
-- opens the notification inbox (POST /api/notifications/read-all).
-- actor_id references users(id): the user who caused the event.
-- entity_id is nullable — identifies the related resource (entry id,
-- friend request id, etc.) when relevant.
-- ============================================================
create table notifications (
    id         uuid        not null default gen_random_uuid(),
    user_id    uuid        not null,
    type       varchar     not null,
    actor_id   uuid        not null,
    entity_id  uuid,
    is_read    boolean     not null default false,
    created_at timestamptz not null default now(),

    constraint notifications_pkey    primary key (id),
    constraint notifications_user_fk foreign key (user_id) references users(id) on delete cascade,
    constraint notifications_type_chk check (type in (
        'FRIEND_REQUEST','FRIEND_ACCEPTED','FRIEND_FINISHED_BOOK','REVIEW_LIKED'
    ))
);

-- Composite index: user_id + is_read + created_at DESC supports
-- both countByUserIdAndIsReadFalse and findByUserIdOrderByCreatedAtDesc queries.
create index notifications_user_read_idx on notifications (user_id, is_read, created_at desc);
