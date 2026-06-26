/**
 * ProtectedRoute.tsx — Route guard that checks localStorage for a JWT token (UI-02)
 *
 * Renders <Outlet /> when token is present; redirects to /login otherwise.
 */
import { Navigate, Outlet } from 'react-router-dom';
import { TOKEN_KEY } from '../../lib/api';

export function ProtectedRoute() {
  const token = localStorage.getItem(TOKEN_KEY);
  return token ? <Outlet /> : <Navigate to="/login" replace />;
}
