/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'path';

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg'],
      manifest: {
        name: 'BookTracker',
        short_name: 'Books',
        description: 'Your personal reading tracker',
        theme_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: 'icons/pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/pwa-512x512.png', sizes: '512x512', type: 'image/png' },
        ],
      },
      workbox: {
        runtimeCaching: [
          {
            // WR-03: same-origin only — an unanchored /\/api\/.*/ regex would
            // match any third-party origin whose URL contains '/api/'.
            // Note: 'api-cache' is purged on sign-out (ProfilePage.handleSignOut)
            // because it stores authenticated personal responses.
            urlPattern: ({ sameOrigin, url }) =>
              sameOrigin && url.pathname.startsWith('/api/'),
            handler: 'NetworkFirst',
            options: {
              cacheName: 'api-cache',
              expiration: { maxEntries: 200, maxAgeSeconds: 24 * 60 * 60 },
            },
          },
          {
            urlPattern: /^https:\/\/covers\.openlibrary\.org\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'book-covers',
              cacheableResponse: { statuses: [0, 200] },
              expiration: { maxEntries: 300, maxAgeSeconds: 30 * 24 * 60 * 60 },
            },
          },
          {
            // AVATAR-07: Dicebear avatars work offline. Not in the sign-out
            // purge list — this cache holds no authenticated data.
            urlPattern: /^https:\/\/api\.dicebear\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'dicebear-avatars',
              cacheableResponse: { statuses: [0, 200] },
              expiration: { maxEntries: 200, maxAgeSeconds: 7 * 24 * 60 * 60 },
            },
          },
        ],
      },
    }),
  ],
  define: {
    // sockjs-client references Node.js `global`; polyfill it for the browser.
    global: 'globalThis',
    // MOB-01: bake the build version string into the bundle at compile time.
    // On Render, RENDER_GIT_COMMIT is a platform env var (short SHA available
    // via slice(0,7)). Locally it falls back to 'dev'. The date component
    // makes every build unique even when RENDER_GIT_COMMIT is absent.
    __APP_VERSION__: JSON.stringify(`${process.env.RENDER_GIT_COMMIT?.slice(0, 7) ?? 'dev'}-${new Date().toISOString().slice(0, 10)}`),
  },
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8080', changeOrigin: true, ws: true },
    },
  },
  preview: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8080', changeOrigin: true, ws: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
  },
});
