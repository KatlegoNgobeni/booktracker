/**
 * LoginPage.tsx — Email + password login form (UI-01)
 *
 * Calls POST /api/auth/login, stores JWT in localStorage under TOKEN_KEY,
 * then navigates to /shelf.
 */
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { BookOpen } from 'lucide-react';
import { api, TOKEN_KEY } from '../../lib/api';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';

interface LoginResponse {
  token: string;
}

export function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const loginMutation = useMutation({
    mutationFn: () =>
      api.post<LoginResponse>('/auth/login', { email, password }).then((r) => r.data),
    onSuccess: (data) => {
      localStorage.setItem(TOKEN_KEY, data.token);
      navigate('/shelf');
    },
    onError: () => {
      setError('Invalid email or password.');
    },
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    loginMutation.mutate();
  }

  return (
    <div className="grid min-h-screen sm:grid-cols-2">
      {/* Left: Branding panel — order-2 on mobile so form appears first */}
      <div className="flex flex-col items-center justify-center gap-4 bg-primary text-primary-foreground p-8 py-10 sm:py-8 order-2 sm:order-1">
        <BookOpen className="h-10 w-10" aria-hidden="true" />
        <div className="text-center">
          <p className="text-[28px] font-semibold leading-tight">BookTracker</p>
          <p className="mt-1 text-sm text-primary-foreground/70">
            Your reading life, organized.
          </p>
        </div>
      </div>

      {/* Right: Form panel — order-1 on mobile so it appears first */}
      <div className="flex items-center justify-center p-6 order-1 sm:order-2">
        <div className="w-full max-w-sm space-y-6">
          <div>
            <h1 className="text-[28px] font-semibold">Sign in</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Welcome back to your reading tracker
            </p>
          </div>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="current-password"
              />
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button type="submit" className="w-full" disabled={loginMutation.isPending}>
              {loginMutation.isPending ? 'Signing in…' : 'Sign In'}
            </Button>
          </form>
          <p className="text-center text-sm text-muted-foreground">
            No account?{' '}
            <Link to="/register" className="underline">
              Create one
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
