/**
 * ProtectedRoute.test.tsx — Route guard behavior tests (UI-02)
 *
 * Test 1: Renders <Outlet/> when booktracker_token is in localStorage
 * Test 2: Redirects to /login when no token present
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route, Outlet } from 'react-router-dom';
import { ProtectedRoute } from './ProtectedRoute';
import { TOKEN_KEY } from '../../lib/api';

// Helper wrapper for routing
function renderWithRouter(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route element={<ProtectedRoute />}>
          <Route path="/shelf" element={<div data-testid="protected-content">Protected!</div>} />
        </Route>
        <Route path="/login" element={<div data-testid="login-page">Login</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('Test 1: renders Outlet child when token exists in localStorage', () => {
    localStorage.setItem(TOKEN_KEY, 'valid-token');
    renderWithRouter('/shelf');

    expect(screen.getByTestId('protected-content')).toBeInTheDocument();
    expect(screen.queryByTestId('login-page')).not.toBeInTheDocument();
  });

  it('Test 2: redirects to /login when no token present', () => {
    localStorage.removeItem(TOKEN_KEY);
    renderWithRouter('/shelf');

    expect(screen.getByTestId('login-page')).toBeInTheDocument();
    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
  });
});

// Suppress unused import warning from the test helper
const _Outlet = Outlet;
void _Outlet;
