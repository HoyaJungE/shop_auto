import { apiClient } from './axiosInstance';
import { ApiResponse } from '../types/api';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  userId: number;
}

/**
 * 로그인
 */
export const login = async (body: LoginRequest): Promise<TokenResponse> => {
  const { data } = await apiClient.post<ApiResponse<TokenResponse>>('/api/v1/auth/login', body);
  if (!data.success || !data.data) throw new Error(data.error?.message ?? '로그인 실패');
  return data.data;
};

/**
 * 로그아웃 (서버 Refresh Token 무효화)
 */
export const logout = async (): Promise<void> => {
  await apiClient.post('/api/v1/auth/logout');
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
};
