-- V3__friend_requests.sql
-- Friend request social graph: PENDING → ACCEPTED / REJECTED / CANCELLED
-- D-01: Mutual friendship model replacing unidirectional follows for social graph.
-- D-02: The existing follows table is NOT touched — friend_requests is added alongside it.
-- This migration is immutable once applied.

-- ============================================================
-- TABLE: friend_requests
-- One request per ordered pair (requester_id, recipient_id).
-- friend_requests_pair_uq prevents duplicate rows for the same direction.
-- friend_requests_no_self prevents self-friend-request at DB level (service checks first).
-- friend_requests_status_chk enforces the four-state lifecycle.
-- ============================================================
create table friend_requests (
    id           uuid        not null default gen_random_uuid(),
    requester_id uuid        not null,
    recipient_id uuid        not null,
    status       varchar     not null default 'PENDING',
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),

    constraint friend_requests_pkey        primary key (id),
    constraint friend_requests_requester_fk foreign key (requester_id) references users(id) on delete cascade,
    constraint friend_requests_recipient_fk foreign key (recipient_id) references users(id) on delete cascade,
    constraint friend_requests_pair_uq      unique (requester_id, recipient_id),
    constraint friend_requests_no_self      check (requester_id <> recipient_id),
    constraint friend_requests_status_chk   check (status in ('PENDING','ACCEPTED','REJECTED','CANCELLED'))
);

-- Index on recipient_id for efficient pending-received queries (GET /api/friend-requests/pending-received)
create index friend_requests_recipient_idx on friend_requests (recipient_id);

-- Index on requester_id for efficient outbound request lookups
create index friend_requests_requester_idx on friend_requests (requester_id);
