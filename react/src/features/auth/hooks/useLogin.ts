import { useMutation } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import { authApi } from '../api';

/**
 * 로그인 mutation 훅
 *
 * 사용 예:
 *   const { mutate: login, isPending } = useLogin();
 *   login({ email, password }, { onError: (e) => alert(e.message) });
 */
export function useLogin() {
  const setTokens = useAuthStore((s) => s.setTokens);

  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      authApi.login(email, password),
    onSuccess: (data) => {
      setTokens(data.accessToken, data.refreshToken, data.userId);
    },
  });
}
