import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { productApi, type ProductSummary, type ProductStatus, type Platform } from '@/api/productApi';

const STATUS_LABEL: Record<ProductStatus, string> = {
  RAW: '수집됨',
  READY: '준비됨',
  PUBLISHING: '등록 중',
  DONE: '완료',
  ERROR: '오류',
};

const STATUS_STYLE: Record<ProductStatus, React.CSSProperties> = {
  RAW:        { background: '#f1f5f9', color: '#64748b' },
  READY:      { background: '#dbeafe', color: '#1d4ed8' },
  PUBLISHING: { background: '#fef3c7', color: '#92400e' },
  DONE:       { background: '#dcfce7', color: '#15803d' },
  ERROR:      { background: '#fee2e2', color: '#b91c1c' },
};

const PLATFORMS: Platform[] = ['COUPANG', 'NAVER', 'OHOUSE'];
const PLATFORM_SHORT: Record<Platform, string> = {
  COUPANG: '쿠팡',
  NAVER: '네이버',
  OHOUSE: '오늘의집',
};

export default function ProductListPage() {
  const qc = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<ProductStatus | ''>('');
  const [page, setPage] = useState(0);
  const [toast, setToast] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['products', statusFilter, page],
    queryFn: () => productApi.getProducts(statusFilter || undefined, page, 20),
  });

  const showToast = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(''), 3000);
  };

  const registerAllMutation = useMutation({
    mutationFn: (id: number) => productApi.registerAll(id),
    onSuccess: (_, id) => {
      showToast(`상품 #${id} 전 플랫폼 등록 요청 완료`);
      qc.invalidateQueries({ queryKey: ['products'] });
    },
    onError: () => showToast('등록 요청 실패'),
  });

  const retryMutation = useMutation({
    mutationFn: ({ id, platform }: { id: number; platform: Platform }) =>
      productApi.retry(id, platform),
    onSuccess: (_, { id, platform }) => {
      showToast(`${PLATFORM_SHORT[platform]} 재시도 요청 완료 (상품 #${id})`);
      qc.invalidateQueries({ queryKey: ['products'] });
    },
    onError: () => showToast('재시도 요청 실패'),
  });

  return (
    <div style={styles.page}>
      <div style={styles.header}>
        <h1 style={styles.title}>📦 상품 목록</h1>
        <div style={styles.filters}>
          {(['', 'RAW', 'READY', 'PUBLISHING', 'DONE', 'ERROR'] as const).map((s) => (
            <button
              key={s}
              style={{ ...styles.filterBtn, ...(statusFilter === s ? styles.filterBtnActive : {}) }}
              onClick={() => { setStatusFilter(s); setPage(0); }}
            >
              {s === '' ? '전체' : STATUS_LABEL[s]}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div style={styles.loading}>불러오는 중...</div>
      ) : (
        <>
          <div style={styles.tableWrap}>
            <table style={styles.table}>
              <thead>
                <tr>
                  <th style={styles.th}>상품명</th>
                  <th style={styles.th}>카테고리</th>
                  <th style={styles.th}>판매가</th>
                  <th style={styles.th}>상태</th>
                  {PLATFORMS.map((p) => (
                    <th key={p} style={styles.th}>{PLATFORM_SHORT[p]}</th>
                  ))}
                  <th style={styles.th}>액션</th>
                </tr>
              </thead>
              <tbody>
                {data?.content.map((product) => (
                  <ProductRow
                    key={product.id}
                    product={product}
                    onRegisterAll={() => registerAllMutation.mutate(product.id)}
                    onRetry={(platform) => retryMutation.mutate({ id: product.id, platform })}
                  />
                ))}
                {data?.content.length === 0 && (
                  <tr>
                    <td colSpan={7} style={{ textAlign: 'center', padding: 40, color: '#94a3b8' }}>
                      상품이 없습니다
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {/* 페이지네이션 */}
          {data && data.totalPages > 1 && (
            <div style={styles.pagination}>
              <button style={styles.pageBtn} disabled={page === 0} onClick={() => setPage(p => p - 1)}>
                ← 이전
              </button>
              <span style={{ fontSize: 14, color: '#475569' }}>
                {page + 1} / {data.totalPages} 페이지 (총 {data.totalElements}개)
              </span>
              <button style={styles.pageBtn} disabled={page >= data.totalPages - 1} onClick={() => setPage(p => p + 1)}>
                다음 →
              </button>
            </div>
          )}
        </>
      )}

      {/* 토스트 알림 */}
      {toast && <div style={styles.toast}>{toast}</div>}
    </div>
  );
}

function ProductRow({
  product,
  onRegisterAll,
  onRetry,
}: {
  product: ProductSummary;
  onRegisterAll: () => void;
  onRetry: (p: Platform) => void;
}) {
  return (
    <tr style={styles.tr}>
      <td style={{ ...styles.td, maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {product.name}
      </td>
      <td style={styles.td}>{product.categoryName ?? '-'}</td>
      <td style={styles.td}>{product.salePrice.toLocaleString()}원</td>
      <td style={styles.td}>
        <span style={{ ...styles.badge, ...STATUS_STYLE[product.status] }}>
          {STATUS_LABEL[product.status]}
        </span>
      </td>
      {/* 플랫폼 상태는 상세 API에서만 오므로 등록 버튼만 표시 */}
      {PLATFORMS.map((platform) => (
        <td key={platform} style={styles.td}>
          {product.status === 'ERROR' ? (
            <button style={styles.retryBtn} onClick={() => onRetry(platform)}>재시도</button>
          ) : (
            <span style={{ color: '#cbd5e1', fontSize: 12 }}>-</span>
          )}
        </td>
      ))}
      <td style={styles.td}>
        {(product.status === 'READY' || product.status === 'RAW') && (
          <button style={styles.actionBtn} onClick={onRegisterAll}>전체 등록</button>
        )}
      </td>
    </tr>
  );
}

const styles: Record<string, React.CSSProperties> = {
  page:           { padding: '24px 32px', maxWidth: 1200, margin: '0 auto', fontFamily: 'system-ui, sans-serif' },
  header:         { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24, flexWrap: 'wrap', gap: 12 },
  title:          { fontSize: 24, fontWeight: 700, color: '#1e293b', margin: 0 },
  filters:        { display: 'flex', gap: 8, flexWrap: 'wrap' },
  filterBtn:      { padding: '6px 14px', borderRadius: 20, border: '1px solid #e2e8f0', background: '#fff', cursor: 'pointer', fontSize: 13, color: '#475569' },
  filterBtnActive:{ background: '#6366f1', color: '#fff', borderColor: '#6366f1' },
  loading:        { padding: 48, textAlign: 'center', color: '#94a3b8' },
  tableWrap:      { background: '#fff', borderRadius: 12, boxShadow: '0 1px 4px rgba(0,0,0,.08)', overflow: 'hidden' },
  table:          { width: '100%', borderCollapse: 'collapse' },
  th:             { textAlign: 'left', padding: '12px 16px', fontSize: 13, color: '#64748b', background: '#f8fafc', borderBottom: '2px solid #e2e8f0' },
  tr:             { borderBottom: '1px solid #f1f5f9' },
  td:             { padding: '12px 16px', fontSize: 14, color: '#334155' },
  badge:          { padding: '3px 10px', borderRadius: 12, fontSize: 12, fontWeight: 600 },
  actionBtn:      { padding: '5px 12px', background: '#6366f1', color: '#fff', border: 'none', borderRadius: 6, fontSize: 12, cursor: 'pointer', fontWeight: 600 },
  retryBtn:       { padding: '4px 10px', background: '#fef3c7', color: '#92400e', border: '1px solid #fcd34d', borderRadius: 6, fontSize: 12, cursor: 'pointer' },
  pagination:     { display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 16, padding: 24 },
  pageBtn:        { padding: '8px 16px', border: '1px solid #e2e8f0', borderRadius: 8, background: '#fff', cursor: 'pointer', fontSize: 14 },
  toast:          { position: 'fixed', bottom: 24, right: 24, background: '#1e293b', color: '#fff', padding: '12px 20px', borderRadius: 10, fontSize: 14, boxShadow: '0 4px 12px rgba(0,0,0,.2)' },
};
