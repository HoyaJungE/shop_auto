import { QueryClient } from '@tanstack/react-query';

/**
 * React Query 클라이언트 설정
 * App.tsx에서 분리해 테스트에서도 독립적으로 사용 가능
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60,        // 1분: 이 시간 내엔 재요청 없음
      gcTime: 1000 * 60 * 5,       // 5분: 미사용 캐시 유지 시간
      retry: 1,                    // 실패 시 1회 재시도
      refetchOnWindowFocus: false, // 탭 전환 시 자동 재요청 비활성화
    },
    mutations: {
      retry: 0,                    // mutation은 재시도 없음
    },
  },
});
