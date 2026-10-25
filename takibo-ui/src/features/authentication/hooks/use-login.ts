import { useMutation } from '@tanstack/react-query';

import { authenticate } from '@/features/authentication/api/login-api';

export function useLogin() {
  return useMutation({
    mutationFn: authenticate,
  });
}
