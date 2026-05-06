import { apiClient } from './axiosInstance';

// ── 타입 ──────────────────────────────────────────────────────

export type ProductStatus = 'RAW' | 'READY' | 'PUBLISHING' | 'DONE' | 'ERROR';
export type Platform = 'COUPANG' | 'NAVER' | 'OHOUSE';
export type RegStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface ProductSummary {
  id: number;
  cafe24ProductId: string;
  name: string;
  salePrice: number;
  categoryName: string;
  status: ProductStatus;
}

export interface Registration {
  platform: Platform;
  status: RegStatus;
  platformProductId: string | null;
  errorMessage: string | null;
}

export interface ProductDetail extends ProductSummary {
  originalPrice: number;
  description: string;
  images: { url: string; order: number; type: string }[];
  options: { group: string; value: string; stockQty: number }[];
  registrations: Registration[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface DashboardStats {
  total: number;
  done: number;
  error: number;
  publishing: number;
  ready: number;
  raw: number;
  platformStats: Record<Platform, { success: number; failed: number; running: number }>;
}

// ── API 함수 ───────────────────────────────────────────────────

export const productApi = {
  // 상품 목록
  getProducts: async (status?: ProductStatus, page = 0, size = 20): Promise<PageResponse<ProductSummary>> => {
    const params: Record<string, unknown> = { page, size };
    if (status) params.status = status;
    const { data } = await apiClient.get('/api/v1/products', { params });
    return data.data;
  },

  // 상품 상세
  getProduct: async (id: number): Promise<ProductDetail> => {
    const { data } = await apiClient.get(`/api/v1/products/${id}`);
    return data.data;
  },

  // 플랫폼 등록
  register: async (id: number, platform: Platform): Promise<{ taskId: string }> => {
    const { data } = await apiClient.post(`/api/v1/products/${id}/register/${platform}`);
    return data.data;
  },

  // 전 플랫폼 일괄 등록
  registerAll: async (id: number): Promise<{ taskIds: string[] }> => {
    const { data } = await apiClient.post(`/api/v1/products/${id}/register/all`);
    return data.data;
  },

  // 실패 재시도
  retry: async (id: number, platform: Platform): Promise<{ taskId: string }> => {
    const { data } = await apiClient.post(`/api/v1/products/${id}/register/${platform}/retry`);
    return data.data;
  },

  // Cafe24 크롤링 요청
  crawl: async (shopUrl: string, limit?: number): Promise<{ taskId: string }> => {
    const { data } = await apiClient.post('/api/v1/products/crawl', null, {
      params: { shopUrl, limit },
    });
    return data.data;
  },
};

export const dashboardApi = {
  getStats: async (): Promise<DashboardStats> => {
    const { data } = await apiClient.get('/api/v1/dashboard/stats');
    return data.data;
  },
};
