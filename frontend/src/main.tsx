import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { registerSW } from 'virtual:pwa-register';
import './index.css';
import App from './App.tsx';
import { ErrorBoundary } from './components/shared/ErrorBoundary.tsx';
// Singleton shared with the api.ts 401 interceptor — the provider and the
// interceptor MUST operate on the same instance (12-07 auth-boundary fix).
import { queryClient } from './lib/queryClient';

// MOB-06: register the Workbox service worker produced by vite-plugin-pwa.
// `immediate: true` triggers a page reload when a new SW takes control (autoUpdate mode).
// The visibility listener re-checks whenever the user returns to the tab/app —
// critical for iOS standalone PWA which resumes from memory without navigating.
// Source: https://vite-pwa-org.netlify.app/guide/auto-update +
//         https://vite-pwa-org.netlify.app/guide/periodic-sw-updates
const updateSW = registerSW({
  immediate: true,
  onRegisteredSW(_swUrl: string, registration: ServiceWorkerRegistration | undefined) {
    // Periodic polling every 60 minutes (fallback for long-lived sessions)
    setInterval(() => {
      if (navigator.onLine) registration?.update();
    }, 60 * 60 * 1000);

    // iOS-standalone resume trigger: standalone PWAs resume from memory
    // without navigating, so poll on visibility regain as well.
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible' && navigator.onLine) {
        registration?.update();
      }
    });
  },
  onNeedRefresh() {
    // autoUpdate mode: apply the new SW silently without prompting the user.
    updateSW(true);
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  </StrictMode>
);
