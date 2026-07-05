/**
 * useWebSocket.ts — STOMP/SockJS WebSocket lifecycle hook (Phase 9 — NOTIF-02)
 *
 * Manages the @stomp/stompjs Client lifecycle inside a React hook.
 * Connects to /ws via SockJS, authenticates at the STOMP CONNECT frame layer,
 * and subscribes to /user/queue/notifications.
 *
 * Security notes (threat model T-09-19):
 * - JWT is sent in the STOMP connectHeaders (Authorization Bearer), NOT in the /ws URL
 *   (no query-param exposure in server logs or browser history).
 * - Server validates JWT via JwtChannelInterceptor on the STOMP CONNECT frame.
 *
 * Known limitation (RESEARCH Pitfall 7 — stale-JWT-on-reconnect):
 * - @stomp/stompjs re-sends the connectHeaders captured at hook initialization on every
 *   auto-reconnect. If the JWT expires during a long session, the reconnect will fail at
 *   the STOMP layer (no valid principal) but the client will not surface an explicit error.
 *   Notifications will stop arriving without user feedback.
 *   Workaround: page refresh after JWT expiry (24h expiry makes this unlikely in practice).
 *   A token-factory pattern (re-reading from localStorage in beforeConnect) would fix this
 *   but adds complexity out of scope for MVP.
 */
import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { NotificationDto } from '../types/api.types';

/**
 * useWebSocket — STOMP client lifecycle hook
 *
 * @param token - JWT string from localStorage (null when logged out)
 * @param onNotification - callback invoked with each pushed NotificationDto
 *
 * Lifecycle:
 * - When `token` is present: creates a Client, activates it, subscribes on connect.
 * - Cleanup: deactivates the client when `token` changes or the component unmounts.
 *
 * Mount location: AppHeader (inside AppLayout) so the WS connection persists across
 * page navigation (D-07).
 */
export function useWebSocket(
  token: string | null,
  onNotification: (payload: NotificationDto) => void
) {
  const clientRef = useRef<Client | null>(null);
  // Stable ref for the callback — avoids reconnect on every render if the function
  // identity changes (e.g., inline arrow in AppHeader).
  const onNotificationRef = useRef(onNotification);
  onNotificationRef.current = onNotification;

  useEffect(() => {
    if (!token) return;

    const client = new Client({
      // Relative URL — Vite dev server proxies /ws → http://localhost:8080 with ws:true.
      // In production the Spring app serves /ws directly from the same origin.
      webSocketFactory: () => new SockJS('/ws'),
      // T-09-19: JWT in STOMP header, not URL query param.
      connectHeaders: { Authorization: `Bearer ${token}` },
      // Auto-reconnect after 5 s on unexpected disconnect.
      reconnectDelay: 5000,
      onConnect: () => {
        // Subscribe to the user-specific notification queue.
        // Spring routes /user/{uuid}/queue/notifications → this subscription.
        client.subscribe('/user/queue/notifications', (msg) => {
          // Separate try/catch scopes: only swallow malformed-payload errors here;
          // callback errors propagate normally so bugs in handleNotification are visible.
          let notif: NotificationDto;
          try {
            notif = JSON.parse(msg.body) as NotificationDto;
          } catch {
            // Malformed payload — ignore silently.
            return;
          }
          onNotificationRef.current(notif);
        });
      },
      // Stop the auto-reconnect loop when the server rejects our STOMP CONNECT
      // (e.g. JWT expired). Without this, @stomp/stompjs retries every reconnectDelay
      // milliseconds indefinitely with the same stale token, hammering the server.
      onStompError: () => {
        client.deactivate();
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [token]); // Reconnect whenever token changes (login/logout)
}
