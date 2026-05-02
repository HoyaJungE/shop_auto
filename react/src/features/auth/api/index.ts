import { apiClient } from '@/api/axiosInstance';
import type { ApiResponse } from '@/types/api';
import type { TokenResponse } from '../types';

/**
 * Auth feature API
 * @/ 경로 alias 사용 (tsconfig.json paths 설정 필요)
 */

export const authApi = {
  login: async (email: string, password: string): Promise<TokenResponse> => {
    const { data } = await apiClient.post<ApiResponse<TokenResponse>>('/api/v1/auth/login', {
      email,
      password,
    });
    if (!data.success || !data.data) throw new Error(data.error?.message ?? '로그인 실패');
    return data.data;
  },

  refresh: async (refreshToken: string): Promise<TokenResponse> => {
    const { data } = await apiClient.post<ApiResponse<TokenResponse>>('/api/v1/auth/refresh', {
      refreshToken,
    });
    if (!data.success || !data.data) throw new Error(data.error?.message ?? '토큰 갱신 실패');
    return data.data;
  },

  logout: async (refreshToken: string): Promise<void> => {
    await apiClient.post('/api/v1/auth/logout', { refreshToken });
  },
};
