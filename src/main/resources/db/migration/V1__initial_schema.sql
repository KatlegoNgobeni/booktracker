-- V1__initial_schema.sql
-- BookTracker initial schema: users, books, user_books, follows, goals
-- UUID primary keys: gen_random_uuid() (built-in from PostgreSQL 13+, available in Postgres 16)
-- shelf_status stored as VARCHAR (EnumType.STRING) — never an ordinal integer.
-- This migration is immutable once applied.

-- ============================================================
-- TABLE: users
-- ============================================================
create table users (
    id            uuid        not null default gen_random_uuid(),
    email         varchar     not null,
    password_hash varchar     not null,
    display_name  varchar     not null,
    created_at    timestamptz not null default now(),

    constraint users_pkey        primary key (id),
    constraint users_email_uq    unique (email)
);

-- ============================================================
-- TABLE: books
-- Stores Open Library work-level data.
-- authors: comma-joined string (nullable — some works have no listed author).
-- cover_id, page_count, first_publish_year: nullable — Open Library often omits them.
-- ============================================================
create table books (
    id                  uuid        not null default gen_random_uuid(),
    open_library_key    varchar     not null,
    title               varchar     not null,
    authors             varchar,
    cover_id            varchar,
    page_count          integer,
    first_publish_year  integer,
    created_at          timestamptz not null default now(),

    constraint books_pkey              primary key (id),
    constraint books_open_library_key_uq unique (open_library_key)
);

-- ============================================================
-- TABLE: user_books
-- One shelf entry per user per book.
-- shelf_status: VARCHAR storing the enum name (WANT_TO_READ / CURRENTLY_READING / READ).
-- rating: 1–5 smallint, nullable.
-- ============================================================
create table user_books (
    id            uuid        not null default gen_random_uuid(),
    user_id       uuid        not null,
    book_id       uuid        not null,
    shelf_status  varchar     not null,
    rating        smallint,
    review        text,
    current_page  integer,
    date_started  date,
    date_finished date,
    created_at    timestamptz not null default now(),

    constraint user_books_pkey             primary key (id),
    constraint user_books_user_fk          foreign key (user_id) references users(id) on delete cascade,
    constraint user_books_book_fk          foreign key (book_id) references books(id),
    constraint user_books_user_book_uq     unique (user_id, book_id)
);

-- ============================================================
-- TABLE: follows
-- Follower/followee social graph.
-- Self-follow prevented by check constraint.
-- ============================================================
create table follows (
    id          uuid        not null default gen_random_uuid(),
    follower_id uuid        not null,
    followee_id uuid        not null,
    created_at  timestamptz not null default now(),

    constraint follows_pkey            primary key (id),
    constraint follows_follower_fk     foreign key (follower_id) references users(id) on delete cascade,
    constraint follows_followee_fk     foreign key (followee_id) references users(id) on delete cascade,
    constraint follows_pair_uq         unique (follower_id, followee_id),
    constraint follows_no_self_follow  check (follower_id <> followee_id)
);

-- ============================================================
-- TABLE: goals
-- One yearly reading goal per user per year.
-- target_count must be non-negative.
-- ============================================================
create table goals (
    id           uuid        not null default gen_random_uuid(),
    user_id      uuid        not null,
    year         integer     not null,
    target_count integer     not null,
    created_at   timestamptz not null default now(),

    constraint goals_pkey              primary key (id),
    constraint goals_user_fk           foreign key (user_id) references users(id) on delete cascade,
    constraint goals_user_year_uq      unique (user_id, year),
    constraint goals_target_count_gte0 check (target_count >= 0)
);

-- ============================================================
-- INDEXES
-- ============================================================

-- Composite index for shelf listing filtered by status (avoids full scan)
create index user_books_user_status_idx  on user_books (user_id, shelf_status);

-- FK index on book_id for join performance
create index user_books_book_idx         on user_books (book_id);

-- FK indexes on follows for efficient follower/followee lookups
create index follows_follower_idx        on follows (follower_id);
create index follows_followee_idx        on follows (followee_id);

-- Index on goals.user_id for goal lookups by user
create index goals_user_idx              on goals (user_id);
