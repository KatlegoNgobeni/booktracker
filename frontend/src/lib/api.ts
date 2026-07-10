/**
 * api.ts — Single Axios instance with JWT interceptors (UI-02)
 *
 * Security notes (from RESEARCH.md Security Domain):
 * - T-06-01: Token stored in localStorage — accepted SPA tradeoff (Phase 2 D-03)
 * - T-06-02: !isAuthEndpoint guard prevents 401 redirect loop when /auth/login returns 401
 * - T-06-03: Redirect target is hardcoded to /login; never honor a ?next= param
 *   (open redirect prevention)
 * - 401 teardown is centralized in clearAuthSession (lib/auth.ts): token +
 *   in-memory query cache + SW api-cache, symmetric with voluntary sign-out
 *   (T-12-07/T-12-08 — closes the auth-boundary cache leak behind UAT 3/4)
 * - Never console.log the token; interceptor reads it directly
 */
import axios from 'axios';
import { clearAuthSession, TOKEN_KEY } from './auth';
import { queryClient } from './queryClient';

// TOKEN_KEY now lives in lib/auth.ts (bottom of the import graph); re-exported
// here so every existing `import { TOKEN_KEY } from '.../lib/api'` site keeps
// compiling. Import direction is api.ts -> auth.ts only, never back.
export { TOKEN_KEY } from './auth';

export const api = axios.create({
  baseURL: '/api',
});

// Request interceptor: attach Bearer token from localStorage
api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor: on 401 (non-auth endpoints) run full session teardown
// (token + query cache + SW api-cache via clearAuthSession), then redirect.
// The !isAuthEndpoint guard prevents a redirect loop when /auth/login returns 401 (bad credentials).
// clearAuthSession is awaited before the redirect so the SW api-cache purge
// completes deterministically (and is never left readable by the next user).
api.interceptors.response.use(
  (res) => res,
  async (err) => {
    const isAuthEndpoint = err.config?.url?.startsWith('/auth/');
    if (err.response?.status === 401 && !isAuthEndpoint) {
      await clearAuthSession(queryClient);
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);
