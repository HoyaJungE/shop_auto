// ────────────────────────────────────────────────────────────
// 공통 타입 정의
// ────────────────────────────────────────────────────────────

export type Platform = 'COUPANG' | 'NAVER' | 'OHOUSE';

export type TaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

// ── Cafe24 크롤링 결과 ────────────────────────────────────────
export interface ProductImage {
  url: string;
  order: number;
  type: 'REPRESENTATIVE' | 'DETAIL';
}

export interface ProductOption {
  groupName: string;   // 예: 색상, 사이즈
  value: string;       // 예: 아이보리
  additionalPrice: number;
  stockQty: number;
}

export interface CrawledProduct {
  cafe24ProductId: string;
  name: string;
  originalPrice: number;
  salePrice: number;
  categoryName: string;
  description: string;  // HTML
  images: ProductImage[];
  options: ProductOption[];
}

// ── 등록 작업 요청/응답 ──────────────────────────────────────
export interface RegisterTask {
  taskId: string;
  platform: Platform;
  product: ProductPayload;
  credentials: PlatformCredentials;
}

export interface ProductPayload {
  cafe24ProductId: string;
  name: string;
  originalPrice: number;
  salePrice: number;
  description: string;
  images: ProductImage[];
  options: ProductOption[];
  categoryCode: string;   // 플랫폼별 카테고리 코드
}

export interface PlatformCredentials {
  loginId: string;
  password: string;
}

export interface TaskResult {
  taskId: string;
  status: TaskStatus;
  platformProductId?: string;   // 등록 성공 시 플랫폼에서 부여한 ID
  errorMessage?: string;
  screenshotPath?: string;      // 실패 시 스크린샷 경로
  completedAt: string;
}

// ── Cafe24 크롤링 요청 ────────────────────────────────────────
export interface CrawlRequest {
  taskId: string;
  credentials: PlatformCredentials & { shopUrl: string };  // 예: https://admin.cafe24.com
  limit?: number;   // 가져올 최대 상품 수 (기본: 전체)
}

export interface CrawlResult {
  taskId: string;
  status: TaskStatus;
  products: CrawledProduct[];
  errorMessage?: string;
}
