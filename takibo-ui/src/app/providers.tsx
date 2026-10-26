import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import type { ReactNode } from 'react';

import { SessionProvider } from '@/shared/security/SessionProvider';

interface AppProvidersProps {
  children: ReactNode;
}

export function AppProviders({ children }: AppProvidersProps) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { retry: 1, refetchOnWindowFocus: false },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <SessionProvider>{children}</SessionProvider>
    </QueryClientProvider>
  );
}
