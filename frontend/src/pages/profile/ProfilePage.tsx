/**
 * ProfilePage.tsx — User identity, dark mode toggle, and sign out (D-03/D-08)
 *
 * No profile editing in MVP (D-08: no PATCH /users/me endpoint exists).
 * Elements:
 *  - Avatar (shadcn) + displayName + email from GET /users/me
 *  - Dark mode Switch (shadcn) using useTheme() hook
 *  - "Sign Out" button — clears booktracker_token + navigates to /login
 *
 * T-06-12: Only authenticated user's own /users/me is shown (server scopes to token subject)
 * TOKEN_KEY imported from api.ts — single source of truth for localStorage key name
 */
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, TOKEN_KEY } from '../../lib/api';
import { QUERY_KEYS } from '../../lib/queryKeys';
import { useTheme } from '../../hooks/useTheme';
import { Avatar, AvatarFallback } from '../../components/ui/avatar';
import { Button } from '../../components/ui/button';
import { Switch } from '../../components/ui/switch';
import { Label } from '../../components/ui/label';
import { Separator } from '../../components/ui/separator';

interface UserMe {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
}

function getInitials(name: string): string {
  return name
    .split(' ')
    .map((n) => n.charAt(0).toUpperCase())
    .slice(0, 2)
    .join('');
}

export function ProfilePage() {
  const navigate = useNavigate();
  const { theme, toggle } = useTheme();
  const { data: me, isPending } = useQuery({
    queryKey: QUERY_KEYS.me(),
    queryFn: () => api.get<UserMe>('/users/me').then((r) => r.data),
  });

  function handleSignOut() {
    localStorage.removeItem(TOKEN_KEY);
    navigate('/login', { replace: true });
  }

  if (isPending) {
    return (
      <div className="p-4 space-y-4">
        <div className="h-16 w-16 rounded-full bg-muted animate-pulse mx-auto" />
        <div className="h-6 bg-muted animate-pulse rounded w-40 mx-auto" />
        <div className="h-4 bg-muted animate-pulse rounded w-56 mx-auto" />
      </div>
    );
  }

  if (!me) return null;

  return (
    <div className="p-4 pb-16 max-w-md mx-auto space-y-6">
      {/* ── Identity ── */}
      <div className="flex flex-col items-center gap-3 pt-4">
        <Avatar className="h-16 w-16">
          <AvatarFallback className="text-xl font-semibold">
            {getInitials(me.displayName)}
          </AvatarFallback>
        </Avatar>
        <div className="text-center">
          <p className="text-xl font-semibold">{me.displayName}</p>
          <p className="text-sm text-muted-foreground">{me.email}</p>
        </div>
      </div>

      <Separator />

      {/* ── Settings ── */}
      <section aria-label="Settings">
        <h2 className="text-base font-semibold mb-3">Preferences</h2>

        {/* Dark mode toggle */}
        <div className="flex items-center justify-between py-2">
          <Label htmlFor="dark-mode-switch" className="text-sm cursor-pointer">
            Dark mode
          </Label>
          <Switch
            id="dark-mode-switch"
            aria-label="Dark mode"
            checked={theme === 'dark'}
            onCheckedChange={toggle}
          />
        </div>
      </section>

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
