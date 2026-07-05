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
import { Bell } from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';
import { TOKEN_KEY } from '../../lib/api';
import { QUERY_KEYS } from '../../lib/queryKeys';
import { useUnreadCount, useMarkAllRead } from '../../hooks/useNotifications';
import { useWebSocket } from '../../hooks/useWebSocket';
import { NotificationSheet } from './NotificationSheet';
import type { NotificationDto } from '../../types/api.types';

export function AppHeader() {
  const [open, setOpen] = useState(false);
  const token = localStorage.getItem(TOKEN_KEY);
  const queryClient = useQueryClient();
  const { data: unreadCount = 0 } = useUnreadCount();
  const { mutate: markAllRead } = useMarkAllRead();

  /**
   * Called by useWebSocket for each pushed notification (NOTIF-02).
   * Invalidates the unread-count and notifications query keys so:
   *   1. The badge number updates immediately without a full page refresh.
   *   2. If the inbox is already open, the list refreshes automatically.
   * T-09-21: payload is typed — no dangerouslySetInnerHTML anywhere in this chain.
   */
  const handleNotification = (_notif: NotificationDto) => {
    queryClient.invalidateQueries({
      queryKey: QUERY_KEYS.notificationsUnreadCount(),
    });
    queryClient.invalidateQueries({
      queryKey: QUERY_KEYS.notifications(),
    });
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
        <span className="text-sm font-medium text-foreground">BookTracker</span>

        {/* Bell button with relative positioning for badge overlay */}
        <button
          aria-label="Notifications"
          className="relative flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
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
      </header>

      {/* Bottom-sheet inbox — rendered outside the header via Sheet portal */}
      <NotificationSheet open={open} onOpenChange={setOpen} />
    </>
  );
}
