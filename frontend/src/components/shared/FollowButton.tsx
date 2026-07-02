/**
 * FollowButton.tsx — Follow / Unfollow toggle button (SOCIAL-01)
 *
 * Presentational toggle: parent controls visibility (hide for own profile — Pitfall 5).
 * Uses useFollowUser / useUnfollowUser mutations from useSocial.ts.
 *
 * Security: T-08F-02 — follower identity comes from the JWT interceptor via the API;
 * no followerId is accepted from props or request body.
 *
 * T-08F-03: The parent (UserPublicProfilePage) is responsible for NOT rendering this
 * component when the viewed profile belongs to the current user.
 */
import { Button } from '../ui/button';
import { useFollowUser, useUnfollowUser } from '../../hooks/useSocial';

interface Props {
  userId: string;
  isFollowing: boolean;
}

export function FollowButton({ userId, isFollowing }: Props) {
  const followMutation = useFollowUser(userId);
  const unfollowMutation = useUnfollowUser(userId);

  if (isFollowing) {
    return (
      <Button
        variant="outline"
        size="sm"
        disabled={unfollowMutation.isPending}
        onClick={() => unfollowMutation.mutate()}
      >
        {unfollowMutation.isPending ? 'Unfollowing…' : 'Unfollow'}
      </Button>
    );
  }

  return (
    <Button
      variant="default"
      size="sm"
      disabled={followMutation.isPending}
      onClick={() => followMutation.mutate()}
    >
      {followMutation.isPending ? 'Following…' : 'Follow'}
    </Button>
  );
}
