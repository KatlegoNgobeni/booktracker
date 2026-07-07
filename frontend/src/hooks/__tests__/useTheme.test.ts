/**
 * useTheme.test.ts — Theme resolution order + toggle behavior (UI-08)
 *
 * Resolution contract: localStorage 'booktracker_theme' → prefers-color-scheme → 'light'.
 *
 * Tests (per 11-01-PLAN.md Task 2 behavior):
 * 1. localStorage 'dark' → initial theme is 'dark'
 * 2. no localStorage + OS prefers dark → initial theme is 'dark'
 * 3. no localStorage + OS does not prefer dark → initial theme is 'light'
 * 4. toggle() flips theme, updates documentElement classList, writes localStorage
 *
 * colorScheme sync (per 11-03-PLAN.md Task 2 behavior): the index.html FOUC
 * bootstrap sets an INLINE style.colorScheme, which outranks the .dark CSS
 * rule — useTheme's theme effect must re-sync it on every theme change.
 * 5. no stored key + OS prefers dark → style.colorScheme is 'dark' on mount
 * 6. OS light → mount 'light'; each toggle() flips style.colorScheme
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useTheme } from '../useTheme';

const THEME_KEY = 'booktracker_theme';

/** Stub window.matchMedia so the hook sees a controlled OS preference. */
function stubMatchMedia(prefersDark: boolean) {
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation((query: string) => ({
      matches: prefersDark,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
  document.documentElement.classList.remove('light', 'dark');
  document.documentElement.style.colorScheme = '';
});

describe('useTheme', () => {
  it('resolves to dark when localStorage booktracker_theme is dark', () => {
    localStorage.setItem(THEME_KEY, 'dark');
    stubMatchMedia(false); // OS preference must be ignored when stored value exists

    const { result } = renderHook(() => useTheme());

    expect(result.current.theme).toBe('dark');
  });

  it('resolves to dark when localStorage is unset and OS prefers dark (UI-08)', () => {
    stubMatchMedia(true);

    const { result } = renderHook(() => useTheme());

    expect(result.current.theme).toBe('dark');
  });

  it('resolves to light when localStorage is unset and OS does not prefer dark', () => {
    stubMatchMedia(false);

    const { result } = renderHook(() => useTheme());

    expect(result.current.theme).toBe('light');
  });

  it('toggle() flips theme, updates documentElement classList, and writes localStorage', () => {
    stubMatchMedia(false);

    const { result } = renderHook(() => useTheme());
    expect(result.current.theme).toBe('light');
    expect(document.documentElement.classList.contains('light')).toBe(true);

    act(() => {
      result.current.toggle();
    });

    expect(result.current.theme).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(document.documentElement.classList.contains('light')).toBe(false);
    expect(localStorage.getItem(THEME_KEY)).toBe('dark');
  });

  it('syncs style.colorScheme to dark on mount when localStorage is unset and OS prefers dark', () => {
    stubMatchMedia(true);

    renderHook(() => useTheme());

    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('re-syncs style.colorScheme on every toggle()', () => {
    stubMatchMedia(false);

    const { result } = renderHook(() => useTheme());
    expect(document.documentElement.style.colorScheme).toBe('light');

    act(() => {
      result.current.toggle();
    });
    expect(document.documentElement.style.colorScheme).toBe('dark');

    act(() => {
      result.current.toggle();
    });
    expect(document.documentElement.style.colorScheme).toBe('light');
  });
});
