/**
 * useTheme.ts — Dark mode hook persisted to localStorage (D-17 Dark Mode Contract)
 *
 * - Resolution order (UI-08): localStorage 'booktracker_theme' → prefers-color-scheme → 'light'
 * - useEffect adds/removes 'light'/'dark' class on document.documentElement
 * - The theme effect also re-syncs root.style.colorScheme: the index.html FOUC
 *   bootstrap pins an INLINE colorScheme pre-paint, and inline styles outrank
 *   the `.dark { color-scheme: dark }` CSS rule — without this re-sync a runtime
 *   toggle would flip tokens via the class but leave native scrollbars/form
 *   controls in the stale scheme
 * - Persists to localStorage ONLY on explicit toggle() (WR-05) — an OS-derived
 *   theme is never written, so the UI-08 fallback chain keeps following the OS
 *   until the user makes a choice (incl. live prefers-color-scheme changes)
 * - toggle() flips between 'light' and 'dark' and persists the choice
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
    // Re-sync the native canvas: the FOUC bootstrap's inline colorScheme
    // outranks the .dark CSS rule, so it must track every theme change.
    root.style.colorScheme = theme;
    // WR-04: keep the browser/status-bar chrome in sync with the APP theme.
    // #0a0a0a matches the dark --background token oklch(0.145 0 0).
    document
      .querySelector('meta[name="theme-color"]:not([media])')
      ?.setAttribute('content', theme === 'dark' ? '#0a0a0a' : '#ffffff');
  }, [theme]);

  // WR-05: while the user has never explicitly chosen a theme, follow live
  // OS preference changes (e.g. scheduled dark mode at sunset).
  useEffect(() => {
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = (e: MediaQueryListEvent) => {
      if (localStorage.getItem(THEME_KEY) === null) {
        setTheme(e.matches ? 'dark' : 'light');
      }
    };
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, []);

  // WR-05: persist ONLY on explicit user action — never the OS-derived snapshot.
  const toggle = () =>
    setTheme((t) => {
      const next = t === 'light' ? 'dark' : 'light';
      localStorage.setItem(THEME_KEY, next);
      return next;
    });

  return { theme, toggle };
}
