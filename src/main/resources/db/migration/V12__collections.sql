-- V12__collections.sql
-- Named book collections: one list per user, books referenced by ol_key.
-- collections: owned by one user, has a name (unique per user), public/private flag.
-- collection_books: join table using ol_key (not books.id) so books need not be cached first.
-- This migration is immutable once applied.

create table collections (
    id         uuid        not null default gen_random_uuid(),
    user_id    uuid        not null,
    name       varchar     not null,
    is_public  boolean     not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint collections_pkey         primary key (id),
    constraint collections_user_fk      foreign key (user_id) references users(id) on delete cascade,
    constraint collections_user_name_uq unique (user_id, name)
);

create table collection_books (
    collection_id uuid        not null,
    book_ol_key   varchar     not null,
    added_at      timestamptz not null default now(),

    constraint collection_books_pkey          primary key (collection_id, book_ol_key),
    constraint collection_books_collection_fk foreign key (collection_id) references collections(id) on delete cascade
);

create index collections_user_idx     on collections (user_id);
create index collection_books_col_idx on collection_books (collection_id);
