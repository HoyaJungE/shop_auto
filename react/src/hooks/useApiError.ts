import { AxiosError } from 'axios';
import { ApiResponse } from '../types/api';

/**
 * API 에러에서 사용자 메시지를 추출하는 훅
 *
 * 사용 예:
 *   const { getErrorMessage } = useApiError();
 *   const msg = getErrorMessage(error);
 */
export function useApiError() {
  const getErrorMessage = (error: unknown): string => {
    if (error instanceof AxiosError) {
      const apiRes = error.response?.data as ApiResponse<unknown> | undefined;
      if (apiRes?.error?.message) return apiRes.error.message;
      if (error.message) return error.message;
    }
    if (error instanceof Error) return error.message;
    return '알 수 없는 오류가 발생했습니다.';
  };

  return { getErrorMessage };
}
