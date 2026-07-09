-- V7__reading_streaks.sql
-- Schema foundation for reading streaks and pace projections (STATS-01/02/03/04).
-- Additive-only (V6 precedent): last_read_date is per-book recency for pace
-- projections (STATS-04); reading_activity holds one row per user per active
-- day for streak computation (STATS-01/02/03).
--
-- Deliberate exception to the project's UUID-PK convention: reading_activity
-- uses a composite primary key (user_id, activity_date). It is a keyless fact
-- table — the pair IS the natural identity, the ON CONFLICT idempotency anchor
-- (STATS-03), and a free per-user index. A surrogate UUID would add nothing.
-- This migration is immutable once applied.

alter table user_books add column last_read_date date;

create table reading_activity (
    user_id       uuid not null,
    activity_date date not null,

    constraint reading_activity_pkey    primary key (user_id, activity_date),
    constraint reading_activity_user_fk foreign key (user_id) references users(id) on delete cascade
);
-- No extra index: the composite PK's leading user_id column serves the per-user date scan.
