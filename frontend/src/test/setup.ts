import '@testing-library/jest-dom';

// ResizeObserver is not available in jsdom — mock it for Recharts ResponsiveContainer
global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
};

// window.matchMedia is not implemented in jsdom — default stub (no dark preference)
// for useTheme's prefers-color-scheme check (11-01, UI-08). Tests that need a specific
// OS preference override this via vi.stubGlobal('matchMedia', ...).
if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as unknown as MediaQueryList;
}
