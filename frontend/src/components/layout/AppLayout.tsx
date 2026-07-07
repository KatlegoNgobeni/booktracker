/**
 * AppLayout.tsx — Shell with AppHeader + BottomNav for authenticated pages (UI-02, D-07)
 *
 * Structure (Phase 9 — D-07):
 *   <AppHeader /> — sticky top-0, notification bell + live badge (h-12, NOTIF-03)
 *   <main>        — flex-1 scrollable page content
 *   <BottomNav /> — fixed bottom-0, 5 tabs (unchanged from Phase 8)
 *
 * Page shell (UI-01, Phase 11 UI review Fix 1): <Outlet /> is wrapped in a single
 * centered shell div (mx-auto, 2xl max-width, 16px gutter / 24px at sm, 16px top
 * padding) so every routed page inherits the same gutter and centering. Pages must
 * NOT add their own horizontal padding or self-centering max-width wrappers.
 *
 * AppHeader is sticky (occupies 48px in document flow), so <main> does NOT need pt-12.
 * The WebSocket connection (useWebSocket) is mounted inside AppHeader and persists
 * across route changes because AppLayout stays mounted for all authenticated routes.
 */
import { Outlet } from 'react-router-dom';
import { AppHeader } from './AppHeader';
import { BottomNav } from './BottomNav';

export function AppLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <AppHeader />          {/* NEW — Phase 9 (D-07): notification bell lives in AppHeader */}
      <main className="flex-1 pb-16">
        <div className="mx-auto w-full max-w-2xl px-4 sm:px-6 pt-4">
          <Outlet />
        </div>
      </main>
      <BottomNav />
    </div>
  );
}
