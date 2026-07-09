/**
 * AppHeader.tsx — Sticky top app bar with notification bell (Phase 9 — NOTIF-03, D-07, D-08)
 *
 * Mounted in AppLayout above <main> so it persists across all authenticated routes.
 * The notification bell shows a live unread badge driven by REST (useUnreadCount) and
 * refreshed in real time by pushed WebSocket notifications (useWebSocket + STOMP).
 *
 * Interaction model (D-08):
 * - Clicking the bell opens the NotificationSheet inline.
 * - All notifications are marked as read immediately on open (mark-all-on-open).
 * - No per-item unread dot — the badge clears when the sheet opens.
 *
 * Security notes:
 * - T-09-19: JWT read from localStorage and placed in STOMP connectHeaders (not the /ws URL).
 * - T-09-20: subscription to /user/queue/notifications is user-scoped via STOMP principal.
 */
import { useState } from 'react';
import { Bell, Sun, Moon } from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';
import { TOKEN_KEY } from '../../lib/api';
import { QUERY_KEYS } from '../../lib/queryKeys';
import { useUnreadCount, useMarkAllRead } from '../../hooks/useNotifications';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { useTheme } from '../../hooks/useTheme';
import { useWebSocket } from '../../hooks/useWebSocket';
import { NotificationSheet } from './NotificationSheet';
import { UserAvatar } from '../shared/UserAvatar';
import type { NotificationDto } from '../../types/api.types';

export function AppHeader() {
  const [open, setOpen] = useState(false);
  const token = localStorage.getItem(TOKEN_KEY);
  const queryClient = useQueryClient();
  const { data: unreadCount = 0 } = useUnreadCount();
  const { mutate: markAllRead, isPending: markAllReadPending } = useMarkAllRead();
  const { data: me } = useCurrentUser();
  const { theme, toggle } = useTheme();

  /**
   * Called by useWebSocket for each pushed notification (NOTIF-02).
   * Always invalidates the unread-count badge. When the sheet is already open,
   * uses refetchQueries (not invalidateQueries) to force an immediate reload of
   * the inbox list — invalidateQueries is a no-op on a disabled query (finding 1).
   * T-09-21: payload is typed — no dangerouslySetInnerHTML anywhere in this chain.
   */
  const handleNotification = (_notif: NotificationDto) => {
    queryClient.invalidateQueries({
      queryKey: QUERY_KEYS.notificationsUnreadCount(),
    });
    if (open) {
      // Sheet is open: force an immediate reload so the new item appears in the list.
      queryClient.refetchQueries({
        queryKey: QUERY_KEYS.notifications(),
      });
    } else {
      // Sheet is closed: mark stale so it reloads when next opened.
      queryClient.invalidateQueries({
        queryKey: QUERY_KEYS.notifications(),
      });
    }
    // A friend finished a book — invalidate the feed so it updates without a page refresh.
    if (_notif.type === 'FRIEND_FINISHED_BOOK') {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.feed() });
    }
  };

  // Mount the STOMP/SockJS connection at AppHeader level (inside AppLayout) so the
  // WebSocket persists across page navigation. Token is read once on render; a page
  // refresh is required after JWT expiry (RESEARCH Pitfall 7 known limitation).
  useWebSocket(token, handleNotification);

  /**
   * Bell click handler — D-08:
   * Open the bottom-sheet inbox and immediately mark all notifications as read.
   * The badge clears once the mutation's onSuccess invalidates the unread-count key.
   */
  const handleBellClick = () => {
    setOpen(true);
    markAllRead();
  };

  return (
    <>
      {/* Sticky top bar — h-12 (48px) as specified in UI-SPEC section 1 */}
      <header className="sticky top-0 z-40 flex h-12 items-center justify-between border-b bg-background px-4">
        {/* Identity — avatar + display name via shared useCurrentUser (AVATAR-06).
            While the query is pending the slot stays empty; the fixed h-12 row
            height guarantees zero layout jump when identity resolves (UI-SPEC 2). */}
        <div className="flex items-center gap-2 min-w-0">
          {me && (
            <>
              <UserAvatar userId={me.id} displayName={me.displayName} size="sm" />
              <span className="text-sm font-semibold text-foreground truncate">
                {me.displayName}
              </span>
            </>
          )}
        </div>

        <div className="flex items-center gap-1">
          {/* Dark-mode toggle — Sun shown in dark mode, Moon in light (UI-02, 44x44 hit area) */}
          <button
            aria-label="Toggle dark mode"
            className="flex h-11 w-11 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
            onClick={toggle}
          >
            {theme === 'dark' ? (
              <Sun className="h-5 w-5" aria-hidden="true" />
            ) : (
              <Moon className="h-5 w-5" aria-hidden="true" />
            )}
          </button>

          {/* Bell button with relative positioning for badge overlay (UI-02, 44x44 hit area) */}
          <button
            aria-label="Notifications"
            className="relative flex h-11 w-11 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
            disabled={markAllReadPending}
            onClick={handleBellClick}
          >
            <Bell className="h-5 w-5" aria-hidden="true" />

            {/* Unread badge — only rendered when count > 0 (UI-SPEC section 1) */}
            {unreadCount > 0 && (
              <span className="absolute -right-1 -top-1 flex min-w-[18px] h-[18px] items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-medium text-destructive-foreground">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </button>
        </div>
      </header>

      {/* Bottom-sheet inbox — rendered outside the header via Sheet portal */}
      <NotificationSheet open={open} onOpenChange={setOpen} />
    </>
  );
}
