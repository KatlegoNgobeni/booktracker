/**
 * useTheme.ts — Dark mode hook persisted to localStorage (D-17 Dark Mode Contract)
 *
 * - Resolution order (UI-08): localStorage 'booktracker_theme' → prefers-color-scheme → 'light'
 * - useEffect adds/removes 'light'/'dark' class on document.documentElement
 * - Persists to localStorage key 'booktracker_theme' on every change
 * - toggle() flips between 'light' and 'dark'
 *
 * The inline FOUC script in index.html reads the same key synchronously before
 * React mounts — keep THEME_KEY in sync with that script.
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
    // Respect OS preference on first visit (UI-08)
    if (window.matchMedia('(prefers-color-scheme: dark)').matches) return 'dark';
    return 'light';
  });

  useEffect(() => {
    const root = document.documentElement;
    root.classList.remove('light', 'dark');
    root.classList.add(theme);
    localStorage.setItem(THEME_KEY, theme);
    // WR-04: keep the browser/status-bar chrome in sync with the APP theme.
    // #0a0a0a matches the dark --background token oklch(0.145 0 0).
    document
      .querySelector('meta[name="theme-color"]:not([media])')
      ?.setAttribute('content', theme === 'dark' ? '#0a0a0a' : '#ffffff');
  }, [theme]);

  const toggle = () => setTheme((t) => (t === 'light' ? 'dark' : 'light'));

  return { theme, toggle };
}
