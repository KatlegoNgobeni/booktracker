-- V8__book_subjects.sql
-- Additive: adds Open Library subject storage to books.
-- subjects: pipe-delimited string (nullable, max 1000 chars).
-- NULL = no subject data fetched yet. Empty string is never written.
-- Immutable once applied.
alter table books add column subjects varchar(1000);
