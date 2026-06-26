/**
 * useTheme.ts — Dark mode hook persisted to localStorage (D-17 Dark Mode Contract)
 *
 * - Reads initial theme from localStorage 'booktracker_theme' (default: 'light')
 * - If root element already has 'dark' class (from 06-01 bootstrap), initializes to match it
 * - useEffect adds/removes 'light'/'dark' class on document.documentElement
 * - Persists to localStorage key 'booktracker_theme' on every change
 * - toggle() flips between 'light' and 'dark'
 *
 * Used by ProfilePage dark mode switch (D-08).
 * CSS variables in shadcn components are automatic when 'dark' class is applied.
 */
import { useEffect, useState } from 'react';

type Theme = 'light' | 'dark';

const THEME_KEY = 'booktracker_theme';

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(() => {
    // Check localStorage first
    const stored = localStorage.getItem(THEME_KEY) as Theme | null;
    if (stored === 'light' || stored === 'dark') return stored;
    // If root already has 'dark' class (from bootstrap in main.tsx), match it
    if (document.documentElement.classList.contains('dark')) return 'dark';
    return 'light';
  });

  useEffect(() => {
    const root = document.documentElement;
    root.classList.remove('light', 'dark');
    root.classList.add(theme);
    localStorage.setItem(THEME_KEY, theme);
  }, [theme]);

  const toggle = () => setTheme((t) => (t === 'light' ? 'dark' : 'light'));

  return { theme, toggle };
}
