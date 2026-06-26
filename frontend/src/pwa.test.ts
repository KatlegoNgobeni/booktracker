/**
 * pwa.test.ts — UI-03 PWA wiring regression test
 *
 * Validates the PWA configuration by reading:
 *   1. The built dist/manifest.webmanifest (when present)
 *   2. The vite.config.ts source for workbox runtimeCaching handlers
 *
 * Runs in Vitest node environment (no browser, no service worker runtime).
 */

import { describe, it, expect } from 'vitest';
import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const ROOT = resolve(__dirname, '..');

// ---------------------------------------------------------------------------
// Manifest assertions (against built output if present, else against config)
// ---------------------------------------------------------------------------

describe('PWA manifest — build output (dist/manifest.webmanifest)', () => {
  const manifestPath = resolve(ROOT, 'dist/manifest.webmanifest');

  it('manifest file exists after build', () => {
    expect(existsSync(manifestPath), `manifest.webmanifest not found at ${manifestPath} — run npm run build first`).toBe(true);
  });

  it('manifest name is "BookTracker"', () => {
    if (!existsSync(manifestPath)) return;
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf-8'));
    expect(manifest.name).toBe('BookTracker');
  });

  it('manifest display is "standalone"', () => {
    if (!existsSync(manifestPath)) return;
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf-8'));
    expect(manifest.display).toBe('standalone');
  });

  it('manifest start_url is "/"', () => {
    if (!existsSync(manifestPath)) return;
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf-8'));
    expect(manifest.start_url).toBe('/');
  });

  it('manifest includes 192x192 and 512x512 icons', () => {
    if (!existsSync(manifestPath)) return;
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf-8'));
    const sizes = (manifest.icons as Array<{ sizes: string }>).map((i) => i.sizes);
    expect(sizes).toContain('192x192');
    expect(sizes).toContain('512x512');
  });
});

// ---------------------------------------------------------------------------
// Service worker present in build output
// ---------------------------------------------------------------------------

describe('PWA service worker — build output', () => {
  const swPath = resolve(ROOT, 'dist/sw.js');

  it('dist/sw.js exists after build', () => {
    expect(existsSync(swPath), `sw.js not found at ${swPath} — run npm run build first`).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// Workbox runtimeCaching handlers — vite.config.ts source assertions
// These lock the caching strategy against accidental removal during refactors.
// ---------------------------------------------------------------------------

describe('PWA workbox runtimeCaching — vite.config.ts', () => {
  const configPath = resolve(ROOT, 'vite.config.ts');
  let configSource: string;

  try {
    configSource = readFileSync(configPath, 'utf-8');
  } catch {
    configSource = '';
  }

  it('vite.config.ts is readable', () => {
    expect(configSource.length, 'vite.config.ts is empty or unreadable').toBeGreaterThan(0);
  });

  it('declares a NetworkFirst handler for /api/ routes', () => {
    // The vite.config.ts source contains the regex literal /\/api\/.*/i
    // When read as raw text, the urlPattern line contains 'api' and the
    // cacheName identifies the strategy group. Check both.
    expect(configSource).toContain('api-cache');
    expect(configSource).toContain('NetworkFirst');
  });

  it('declares a CacheFirst handler for covers.openlibrary.org', () => {
    // The source regex literal contains 'openlibrary' as a text token;
    // the cacheName 'book-covers' confirms the CacheFirst group is present.
    expect(configSource).toContain('openlibrary');
    expect(configSource).toContain('book-covers');
    expect(configSource).toContain('CacheFirst');
  });

  it('covers CacheFirst handler includes cacheableResponse statuses [0, 200] for opaque responses', () => {
    expect(configSource).toMatch(/cacheableResponse/);
    expect(configSource).toMatch(/statuses.*\[0.*200\]|statuses.*0.*200/s);
  });

  it('registerType is autoUpdate', () => {
    expect(configSource).toMatch(/registerType.*autoUpdate/);
  });

  it('devOptions.enabled is not set to true (preserves HMR)', () => {
    // Either devOptions block doesn't exist, or enabled is not true
    const devOptionsMatch = configSource.match(/devOptions\s*:\s*\{([^}]*)\}/s);
    if (devOptionsMatch) {
      const block = devOptionsMatch[1];
      expect(block).not.toMatch(/enabled\s*:\s*true/);
    }
    // If no devOptions block at all, that's fine — defaults to disabled
  });
});

// ---------------------------------------------------------------------------
// PWA icon files exist in public/
// ---------------------------------------------------------------------------

describe('PWA icon files', () => {
  it('public/icons/pwa-192x192.png exists', () => {
    expect(existsSync(resolve(ROOT, 'public/icons/pwa-192x192.png'))).toBe(true);
  });

  it('public/icons/pwa-512x512.png exists', () => {
    expect(existsSync(resolve(ROOT, 'public/icons/pwa-512x512.png'))).toBe(true);
  });

  it('public/favicon.svg exists', () => {
    expect(existsSync(resolve(ROOT, 'public/favicon.svg'))).toBe(true);
  });
});
