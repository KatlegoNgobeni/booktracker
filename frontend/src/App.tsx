/**
 * App.tsx — Route tree for BookTracker (UI-02)
 *
 * Public routes:  /login, /register
 * Protected routes (require JWT): /shelf, /search, /stats, /profile,
 *                                  /books/:olKey, /shelf/:id/edit
 */
import { Routes, Route, Navigate } from 'react-router-dom';
import { ProtectedRoute } from './components/layout/ProtectedRoute';
import { AppLayout } from './components/layout/AppLayout';
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';
import { ShelfPage } from './pages/shelf/ShelfPage';
import { SearchPage } from './pages/search/SearchPage';
import { ShelfEntryEditorPage } from './pages/shelf/ShelfEntryEditorPage';
import { BookDetailPage } from './pages/book/BookDetailPage';
import { StatsPage } from './pages/stats/StatsPage';
import { ProfilePage } from './pages/profile/ProfilePage';

export default function App() {
  return (
    <Routes>
      {/* Public */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Protected — wrapped with ProtectedRoute + AppLayout */}
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/shelf" element={<ShelfPage />} />
          <Route path="/shelf/:id/edit" element={<ShelfEntryEditorPage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/books/:olKey" element={<BookDetailPage />} />
          <Route path="/stats" element={<StatsPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
      </Route>

      {/* Default redirect */}
      <Route path="*" element={<Navigate to="/shelf" replace />} />
    </Routes>
  );
}
