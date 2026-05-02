/**
 * 공통 API 응답 타입
 * Spring ApiResponse<T>와 1:1 매핑
 */
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
  };
  timestamp: string;
}

/** 페이지네이션 응답 */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;        // 현재 페이지 (0-based)
  first: boolean;
  last: boolean;
}

/** 페이지 요청 파라미터 */
export interface PageRequest {
  page?: number;
  size?: number;
  sort?: string;         // 예: "createdAt,desc"
}
