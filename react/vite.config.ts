import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tsconfigPaths from 'vite-tsconfig-paths';

/**
 * Vite 설정
 *
 * [주요 설정]
 * - tsconfigPaths: tsconfig의 paths 설정을 자동으로 Vite에 적용
 *   → import '@/api/axiosInstance' 같은 절대경로 사용 가능
 * - server.proxy: 개발 시 API 프록시 (CORS 우회, 토큰 노출 방지)
 */
export default defineConfig({
  plugins: [
    react(),
    tsconfigPaths(),
  ],

  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // rewrite: (path) => path.replace(/^\/api/, ''),  // prefix 제거 필요 시
      },
    },
  },

  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
});
