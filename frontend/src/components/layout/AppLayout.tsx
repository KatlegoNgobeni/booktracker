/**
 * AppLayout.tsx — Shell with BottomNav for authenticated pages (UI-02)
 */
import { Outlet } from 'react-router-dom';
import { BottomNav } from './BottomNav';

export function AppLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <main className="flex-1 pb-16">
        <Outlet />
      </main>
      <BottomNav />
    </div>
  );
}
