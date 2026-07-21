/**
 * ProfilePage.tsx — User identity and sign out (D-03/D-08)
 *
 * No profile editing in MVP (D-08: no PATCH /users/me endpoint exists).
 * Elements:
 *  - UserAvatar (shared, AVATAR-01) + displayName + email from GET /users/me via useCurrentUser
 *  - "Sign Out" button — full session teardown via clearAuthSession + navigate to /login
 *
 * Dark mode is controlled globally from the AppHeader toggle (Phase 11 D-05).
 *
 * T-06-12: Only authenticated user's own /users/me is shown (server scopes to token subject)
 */
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient, useMutation } from '@tanstack/react-query';
import { Camera, Loader2 } from 'lucide-react';
import { clearAuthSession } from '../../lib/auth';
import { api } from '../../lib/api';
import { QUERY_KEYS } from '../../lib/queryKeys';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { usePublicProfile } from '../../hooks/useSocial';
import { UserAvatar } from '../../components/shared/UserAvatar';
import { Button } from '../../components/ui/button';
import { Card, CardContent } from '../../components/ui/card';
import { Input } from '../../components/ui/input';
import { Separator } from '../../components/ui/separator';

export function ProfilePage() {
  const navigate = useNavigate();
  // At runtime this is the lib/queryClient.ts singleton (provided by main.tsx),
  // so voluntary sign-out wipes the same cache the 401 forced-logout path wipes.
  const queryClient = useQueryClient();
  // AVATAR-06 dedupe: same QUERY_KEYS.me() cache entry as AppHeader — one fetch app-wide.
  const { data: me, isPending, isError, refetch } = useCurrentUser();
  const { data: socialProfile } = usePublicProfile(me?.id ?? '');

  // Photo upload mutation — POST /users/me/photo with multipart/form-data (D-01/D-03)
  const uploadMutation = useMutation({
    mutationFn: async (file: File) => {
      const fd = new FormData();
      fd.append('file', file);
      const r = await api.post('/users/me/photo', fd);
      return r.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.me() }),
  });

  // Photo remove mutation — DELETE /users/me/photo (D-03/D-08)
  const removeMutation = useMutation({
    mutationFn: async () => {
      const r = await api.delete('/users/me/photo');
      return r.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.me() }),
  });

  // Account settings form state
  const [newDisplayName, setNewDisplayName] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [settingsError, setSettingsError] = useState('');
  const [settingsSuccess, setSettingsSuccess] = useState('');

  // PATCH /users/me mutation
  const updateMutation = useMutation({
    mutationFn: async (body: { displayName?: string; currentPassword?: string; newPassword?: string }) => {
      const r = await api.patch('/users/me', body);
      return r.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.me() });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setNewDisplayName('');
      setSettingsError('');
      setSettingsSuccess('Saved!');
      setTimeout(() => setSettingsSuccess(''), 3000);
    },
    onError: (err: { response?: { data?: { message?: string } } }) => {
      setSettingsError(err?.response?.data?.message ?? 'Update failed. Try again.');
    },
  });

  function handleSettingsSave(e: React.FormEvent) {
    e.preventDefault();
    setSettingsError('');
    setSettingsSuccess('');

    const body: { displayName?: string; currentPassword?: string; newPassword?: string } = {};

    if (newDisplayName.trim()) body.displayName = newDisplayName.trim();

    if (newPassword) {
      if (newPassword !== confirmPassword) {
        setSettingsError('New passwords do not match.');
        return;
      }
      if (!currentPassword) {
        setSettingsError('Enter your current password to change it.');
        return;
      }
      body.currentPassword = currentPassword;
      body.newPassword = newPassword;
    }

    if (!body.displayName && !body.newPassword) {
      setSettingsError('Enter a new display name or a new password.');
      return;
    }

    updateMutation.mutate(body);
  }

  // Hidden file input ref — triggers native file picker (D-06)
  const fileInputRef = useRef<HTMLInputElement>(null);
  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) uploadMutation.mutate(file);
    e.target.value = '';
  };

  function handleSignOut() {
    // Session teardown is centralized in lib/auth.ts (clearAuthSession):
    // token removal + QueryClient.clear() run synchronously before navigation;
    // the SW 'api-cache' purge (WR-03) continues in the background exactly as
    // the old fire-and-forget code did. Fixes UAT tests 3/4 — cached identity
    // and unread-count previously survived into the next user's session.
    void clearAuthSession(queryClient);
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
            {/* AVATAR-01: 64px avatar with upload overlay (D-06/D-07/D-08) */}
            <div className="relative">
              <UserAvatar
                userId={me.id}
                displayName={me.displayName}
                className="h-16 w-16"
                fallbackClassName="text-xl font-semibold"
                photoUrl={me.photoUrl}
              />
              {uploadMutation.isPending ? (
                <div className="absolute inset-0 rounded-full flex items-center justify-center bg-black/40">
                  <Loader2 className="h-5 w-5 text-white animate-spin" />
                </div>
              ) : (
                <button
                  aria-label="Change profile photo"
                  onClick={() => fileInputRef.current?.click()}
                  className="absolute inset-0 rounded-full flex items-center justify-center bg-black/0 hover:bg-black/40 transition-colors group"
                >
                  <Camera className="h-5 w-5 text-white opacity-0 group-hover:opacity-100 sm:opacity-30" />
                </button>
              )}
              <input
                type="file"
                accept="image/*"
                className="sr-only"
                ref={fileInputRef}
                onChange={handleFileSelect}
              />
            </div>
            {/* Remove photo link — only visible when a photo is set (D-08) */}
            {me.photoUrl && (
              <button
                onClick={() => removeMutation.mutate()}
                className="text-xs text-destructive hover:underline mt-2"
                disabled={removeMutation.isPending}
              >
                {removeMutation.isPending ? 'Removing…' : 'Remove photo'}
              </button>
            )}
            <div className="text-center">
              <p className="text-xl font-semibold">{me.displayName}</p>
              <p className="text-sm text-muted-foreground">{me.email}</p>
            </div>
            {socialProfile && (
              <div className="flex gap-6 text-sm">
                <span><strong className="text-foreground">{socialProfile.friendCount}</strong> <span className="text-muted-foreground">friends</span></span>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {/* ── Account Settings ── */}
      <Card>
        <CardContent className="p-4">
          <h2 className="text-base font-semibold mb-4">Account Settings</h2>
          <form onSubmit={handleSettingsSave} className="space-y-3">
            <div>
              <label className="text-sm font-medium text-foreground">Display name</label>
              <Input
                type="text"
                placeholder={me.displayName}
                value={newDisplayName}
                onChange={(e) => setNewDisplayName(e.target.value)}
                maxLength={50}
                className="mt-1"
              />
            </div>

            <Separator />

            <p className="text-sm font-medium text-foreground">Change password</p>
            <div>
              <label className="text-xs text-muted-foreground">Current password</label>
              <Input
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                autoComplete="current-password"
                className="mt-1"
              />
            </div>
            <div>
              <label className="text-xs text-muted-foreground">New password</label>
              <Input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                autoComplete="new-password"
                className="mt-1"
              />
            </div>
            <div>
              <label className="text-xs text-muted-foreground">Confirm new password</label>
              <Input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                autoComplete="new-password"
                className="mt-1"
              />
            </div>

            {settingsError && (
              <p className="text-sm text-destructive">{settingsError}</p>
            )}
            {settingsSuccess && (
              <p className="text-sm text-green-600 dark:text-green-400">{settingsSuccess}</p>
            )}

            <Button
              type="submit"
              className="w-full"
              disabled={updateMutation.isPending}
            >
              {updateMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : 'Save changes'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Separator />

      {/* ── Sign Out ── */}
      <section>
        <Button
          variant="outline"
          className="w-full"
          data-testid="logout-button"
          onClick={handleSignOut}
        >
          Sign Out
        </Button>
        <p className="text-xs text-muted-foreground text-center mt-2">
          Member since {new Date(me.createdAt).getFullYear()}
        </p>
        {/* MOB-01: build version — diagnosable staleness indicator */}
        <p className="text-xs text-muted-foreground/50 text-center mt-1">
          v{__APP_VERSION__}
        </p>
      </section>
    </div>
  );
}
