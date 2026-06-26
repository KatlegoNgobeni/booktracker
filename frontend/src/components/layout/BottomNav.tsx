/**
 * BottomNav.tsx — Mobile bottom navigation bar (UI-02)
 *
 * Five tabs: Search, Shelf, Stats, Profile.
 */
import { NavLink } from 'react-router-dom';
import { Search, BookOpen, BarChart2, User } from 'lucide-react';

const tabs = [
  { to: '/search', label: 'Search', icon: Search },
  { to: '/shelf', label: 'Shelf', icon: BookOpen },
  { to: '/stats', label: 'Stats', icon: BarChart2 },
  { to: '/profile', label: 'Profile', icon: User },
];

export function BottomNav() {
  return (
    <nav
      className="fixed bottom-0 left-0 right-0 z-50 border-t bg-background"
      aria-label="Main navigation"
    >
      <ul className="flex h-16 items-center justify-around">
        {tabs.map(({ to, label, icon: Icon }) => (
          <li key={to}>
            <NavLink
              to={to}
              className={({ isActive }) =>
                `flex flex-col items-center gap-0.5 px-3 text-xs ${
                  isActive ? 'text-primary' : 'text-muted-foreground'
                }`
              }
            >
              <Icon className="h-5 w-5" aria-hidden="true" />
              <span>{label}</span>
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
