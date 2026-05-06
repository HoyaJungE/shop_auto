import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { queryClient } from '@/lib/queryClient';
import ProtectedRoute from '@/components/common/ProtectedRoute';
import AppLayout from '@/components/layout/AppLayout';

const LoginPage       = lazy(() => import('@/features/auth/pages/LoginPage'));
const DashboardPage   = lazy(() => import('@/features/dashboard/pages/DashboardPage'));
const ProductListPage = lazy(() => import('@/features/products/pages/ProductListPage'));

function PageLoader() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', color: '#94a3b8', fontSize: 14 }}>
      불러오는 중...
    </div>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Suspense fallback={<PageLoader />}>
          <Routes>
            {/* 공개 */}
            <Route path="/login" element={<LoginPage />} />

            {/* 인증 필요 — AppLayout (사이드바) 포함 */}
            <Route element={<ProtectedRoute />}>
              <Route element={<AppLayout />}>
                <Route path="/dashboard" element={<DashboardPage />} />
                <Route path="/products"  element={<ProductListPage />} />
              </Route>
            </Route>

            <Route path="/"  element={<Navigate to="/dashboard" replace />} />
            <Route path="*"  element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </Suspense>
      </BrowserRouter>
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  );
}
