/**
 * queryClient.ts — module-singleton TanStack QueryClient (AVATAR-06, 12-07)
 *
 * Shared by main.tsx (QueryClientProvider) and api.ts (401 forced-logout
 * teardown). Both MUST reference this same instance — if the provider held
 * its own client, the 401 interceptor would clear a dead client and the
 * auth-boundary cache purge would be a no-op.
 *
 * This module sits at the bottom of the import graph (no local imports) so
 * api.ts can import it without cycles. Only clearAuthSession (lib/auth.ts)
 * may clear it wholesale.
 */
import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 min
      retry: 1,
    },
  },
});
