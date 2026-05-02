import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/**
 * 공통 Axios 인스턴스
 * - 요청 시 Authorization 헤더 자동 주입
 * - 401 응답 시 토큰 갱신 후 재시도
 * - 그 외 에러는 BusinessError로 변환하여 throw
 */
export const apiClient = axios.create({
  baseURL: BASE_URL,
  timeout: 10_000,
  headers: { 'Content-Type': 'application/json' },
});

// ── Request 인터셉터: Access Token 주입 ─────────────────────────────────────

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ── Response 인터셉터: 에러 처리 / 토큰 갱신 ────────────────────────────────

let isRefreshing = false;
let pendingQueue: Array<(token: string) => void> = [];

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config!;

    if (error.response?.status === 401 && !(original as any)._retry) {
      (original as any)._retry = true;

      if (isRefreshing) {
        // 이미 갱신 중이면 대기 후 재시도
        return new Promise((resolve) => {
          pendingQueue.push((token) => {
            original.headers.Authorization = `Bearer ${token}`;
            resolve(apiClient(original));
          });
        });
      }

      isRefreshing = true;
      try {
        const refreshToken = localStorage.getItem('refreshToken');
        const { data } = await axios.post(`${BASE_URL}/api/v1/auth/refresh`, { refreshToken });
        const newAccessToken: string = data.data.accessToken;

        localStorage.setItem('accessToken', newAccessToken);
        pendingQueue.forEach((cb) => cb(newAccessToken));
        pendingQueue = [];

        original.headers.Authorization = `Bearer ${newAccessToken}`;
        return apiClient(original);
      } catch (_) {
        // 갱신 실패 → 로그아웃 처리
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login';
        return Promise.reject(error);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  },
);
