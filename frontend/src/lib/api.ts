/**
 * api.ts — Single Axios instance with JWT interceptors (UI-02)
 *
 * Security notes (from RESEARCH.md Security Domain):
 * - T-06-01: Token stored in localStorage — accepted SPA tradeoff (Phase 2 D-03)
 * - T-06-02: !isAuthEndpoint guard prevents 401 redirect loop when /auth/login returns 401
 * - T-06-03: Always redirect to /shelf; never honor a ?next= param (open redirect prevention)
 * - Never console.log the token; interceptor reads it directly
 */
import axios from 'axios';

export const TOKEN_KEY = 'booktracker_token';

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

// Response interceptor: on 401 (non-auth endpoints) clear token + redirect
// The !isAuthEndpoint guard prevents a redirect loop when /auth/login returns 401 (bad credentials)
api.interceptors.response.use(
  (res) => res,
  (err) => {
    const isAuthEndpoint = err.config?.url?.startsWith('/auth/');
    if (err.response?.status === 401 && !isAuthEndpoint) {
      localStorage.removeItem(TOKEN_KEY);
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);
