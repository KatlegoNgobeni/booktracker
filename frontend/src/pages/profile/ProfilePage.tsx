/**
 * ProfilePage.tsx — User identity and sign out (D-03/D-08)
 *
 * No profile editing in MVP (D-08: no PATCH /users/me endpoint exists).
 * Elements:
 *  - UserAvatar (shared, AVATAR-01) + displayName + email from GET /users/me via useCurrentUser
 *  - "Sign Out" button — clears booktracker_token + navigates to /login
 *
 * Dark mode is controlled globally from the AppHeader toggle (Phase 11 D-05).
 *
 * T-06-12: Only authenticated user's own /users/me is shown (server scopes to token subject)
 * TOKEN_KEY imported from api.ts — single source of truth for localStorage key name
 */
import { useNavigate } from 'react-router-dom';
import { TOKEN_KEY } from '../../lib/api';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { usePublicProfile } from '../../hooks/useSocial';
import { UserAvatar } from '../../components/shared/UserAvatar';
import { Button } from '../../components/ui/button';
import { Card, CardContent } from '../../components/ui/card';
import { Separator } from '../../components/ui/separator';

export function ProfilePage() {
  const navigate = useNavigate();
  // AVATAR-06 dedupe: same QUERY_KEYS.me() cache entry as AppHeader — one fetch app-wide.
  const { data: me, isPending, isError, refetch } = useCurrentUser();
  const { data: socialProfile } = usePublicProfile(me?.id ?? '');

  function handleSignOut() {
    localStorage.removeItem(TOKEN_KEY);
    // WR-03: purge SW-cached authenticated API responses so the next user on
    // this device cannot read shelf/profile/feed data from Cache Storage.
    if ('caches' in window) {
      void caches.delete('api-cache').catch(() => {});
    }
    navigate('/login', { replace: true });
  }

  if (isPending) {
    return (
      <div className="space-y-4">
        <div className="h-16 w-16 rounded-full bg-muted animate-pulse mx-auto" />
        <div className="h-6 bg-muted animate-pulse rounded w-40 mx-auto" />
        <div className="h-4 bg-muted animate-pulse rounded w-56 mx-auto" />
      </div>
    );
  }

  // Error state (WR-02): never render a blank page — keep Retry and the
  // Sign Out escape hatch reachable even when GET /users/me fails.
  if (isError || !me) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 px-4 text-center">
        <p className="text-sm text-muted-foreground">Couldn&apos;t load your profile.</p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          Retry
        </Button>
        <Button variant="ghost" size="sm" onClick={handleSignOut}>
          Sign Out
        </Button>
      </div>
    );
  }

  return (
    <div className="pb-16 space-y-6">
      {/* ── Identity ── */}
      <Card>
        <CardContent className="p-4">
          <div className="flex flex-col items-center gap-3">
            {/* AVATAR-01: generated avatar at the existing 64px footprint (UI-SPEC size map) */}
            <UserAvatar
              userId={me.id}
              displayName={me.displayName}
              className="h-16 w-16"
              fallbackClassName="text-xl font-semibold"
            />
            <div className="text-center">
              <p className="text-xl font-semibold">{me.displayName}</p>
              <p className="text-sm text-muted-foreground">{me.email}</p>
            </div>
            {socialProfile && (
              <div className="flex gap-6 text-sm">
                <span><strong className="text-foreground">{socialProfile.followerCount}</strong> <span className="text-muted-foreground">followers</span></span>
                <span><strong className="text-foreground">{socialProfile.followingCount}</strong> <span className="text-muted-foreground">following</span></span>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      <Separator />

      {/* ── Sign Out ── */}
      <section>
        <Button
          variant="outline"
          className="w-full"
          onClick={handleSignOut}
        >
          Sign Out
        </Button>
        <p className="text-xs text-muted-foreground text-center mt-2">
          Member since {new Date(me.createdAt).getFullYear()}
        </p>
      </section>
    </div>
  );
}
