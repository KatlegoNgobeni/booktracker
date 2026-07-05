/**
 * useNotifications.ts — TanStack Query hooks for the notification REST layer (Phase 9)
 *
 * NOTIF-03: Notification bell badge + inbox driven by REST endpoints.
 *   - useUnreadCount()   → GET /api/notifications/unread-count (badge count)
 *   - useNotifications() → GET /api/notifications (inbox list; enabled:false on mount)
 *   - useMarkAllRead()   → POST /api/notifications/read-all (marks all as read, clears badge)
 *
 * Security notes:
 * - T-09-20: Endpoints are authenticated — only the JWT owner's notifications are returned.
 * - T-09-21: Notification text composed in JSX from typed fields; no dangerouslySetInnerHTML.
 *
 * TanStack Query v5 notes:
 * - Use isPending (not isLoading) for mutation pending state.
 * - invalidateQueries requires object form: { queryKey: [...] }.
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { QUERY_KEYS } from '../lib/queryKeys';
import type { NotificationDto } from '../types/api.types';

/**
 * useUnreadCount — query for GET /api/notifications/unread-count
 *
 * Returns the number of unread notifications for the authenticated user.
 * Drives the red badge on the AppHeader bell icon (NOTIF-03).
 */
export function useUnreadCount() {
  return useQuery({
    queryKey: QUERY_KEYS.notificationsUnreadCount(),
    queryFn: () =>
      api
        .get<{ count: number }>('/notifications/unread-count')
        .then((r) => r.data.count),
  });
}

/**
 * useNotifications — query for GET /api/notifications (inbox list)
 *
 * enabled: false on mount — the list only fetches when the NotificationSheet opens
 * (triggered by calling refetch() from the sheet's onOpenChange handler).
 * Keyed by QUERY_KEYS.notifications() — invalidated by useMarkAllRead and useWebSocket push.
 */
export function useNotifications() {
  return useQuery({
    queryKey: QUERY_KEYS.notifications(),
    queryFn: () =>
      api
        .get<NotificationDto[]>('/notifications')
        .then((r) => r.data),
    enabled: false,
  });
}

/**
 * useMarkAllRead — mutation for POST /api/notifications/read-all
 *
 * Marks all of the authenticated user's notifications as read.
 * Called when the NotificationSheet opens (D-08 — mark all read on open).
 * onSuccess: invalidates unread-count (badge drops to 0) and notifications (isRead updates).
 */
export function useMarkAllRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.post('/notifications/read-all'),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: QUERY_KEYS.notificationsUnreadCount(),
      });
      queryClient.invalidateQueries({
        queryKey: QUERY_KEYS.notifications(),
      });
    },
  });
}
