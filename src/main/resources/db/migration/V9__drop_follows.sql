-- Drop the follows table — the social graph is now exclusively mutual friends
-- via friend_requests (V3). No data migration needed; any existing follows rows
-- are superseded by the friend request system.
drop table if exists follows;
