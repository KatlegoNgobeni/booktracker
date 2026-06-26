/**
 * LoginPage.test.tsx — Smoke tests for Login page UI (UI-01)
 *
 * Test 2: LoginPage renders email + password inputs (with <Label htmlFor>) and a "Sign In" button
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { LoginPage } from './LoginPage';

// Mock the api module so no actual HTTP calls happen
vi.mock('../../lib/api', () => ({
  TOKEN_KEY: 'booktracker_token',
  api: {
    post: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
}));

function renderLoginPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('LoginPage', () => {
  it('Test 2: renders email + password inputs and "Sign In" button', () => {
    renderLoginPage();

    // Email input with label
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    // Password input with label
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    // Submit button
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });
});
