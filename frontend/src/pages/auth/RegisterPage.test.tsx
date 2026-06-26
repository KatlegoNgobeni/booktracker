/**
 * RegisterPage.test.tsx — Smoke tests for Register page UI (UI-01)
 *
 * Test 3: RegisterPage renders email + password + display name inputs and "Create Account" button
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RegisterPage } from './RegisterPage';

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

function renderRegisterPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('RegisterPage', () => {
  it('Test 3: renders email, password, display name inputs and "Create Account" button', () => {
    renderRegisterPage();

    // Email input
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    // Password input
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    // Display name input
    expect(screen.getByLabelText(/display name/i)).toBeInTheDocument();
    // Submit button
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
  });
});
