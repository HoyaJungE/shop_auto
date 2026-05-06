import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { dashboardApi, productApi, type DashboardStats } from '@/api/productApi';

const PLATFORM_LABEL: Record<string, string> = {
  COUPANG: '쿠팡 WING',
  NAVER: '네이버 스마트스토어',
  OHOUSE: '오늘의집',
};

const STATUS_COLOR: Record<string, string> = {
  RAW: '#94a3b8',
  READY: '#60a5fa',
  PUBLISHING: '#f59e0b',
  DONE: '#22c55e',
  ERROR: '#ef4444',
};

export default function DashboardPage() {
  const qc = useQueryClient();
  const [shopUrl, setShopUrl] = useState('');
  const [crawlMsg, setCrawlMsg] = useState('');

  const { data: stats, isLoading } = useQuery<DashboardStats>({
    queryKey: ['dashboard-stats'],
    queryFn: dashboardApi.getStats,
    refetchInterval: 15_000, // 15초마다 자동 갱신
  });

  const crawlMutation = useMutation({
    mutationFn: () => productApi.crawl(shopUrl),
    onSuccess: ({ taskId }) => {
      setCrawlMsg(`크롤링 요청 완료 (taskId: ${taskId})`);
      setShopUrl('');
      setTimeout(() => qc.invalidateQueries({ queryKey: ['dashboard-stats'] }), 3000);
    },
    onError: () => setCrawlMsg('크롤링 요청 실패'),
  });

  if (isLoading) return <div style={styles.loading}>통계 불러오는 중...</div>;

  const s = stats!;
  const successRate = s.total > 0 ? Math.round((s.done / s.total) * 100) : 0;

  return (
    <div style={styles.page}>
      <h1 style={styles.title}>📊 대시보드</h1>

      {/* ── 상품 현황 카드 ── */}
      <section style={styles.section}>
        <h2 style={styles.sectionTitle}>상품 현황</h2>
        <div style={styles.cardGrid}>
          <StatCard label="전체 상품" value={s.total} color="#6366f1" />
          <StatCard label="등록 완료" value={s.done} color={STATUS_COLOR.DONE} />
          <StatCard label="진행 중" value={s.publishing} color={STATUS_COLOR.PUBLISHING} />
          <StatCard label="오류" value={s.error} color={STATUS_COLOR.ERROR} />
          <StatCard label="준비됨" value={s.ready} color={STATUS_COLOR.READY} />
          <StatCard label="수집만됨" value={s.raw} color={STATUS_COLOR.RAW} />
        </div>
        <div style={styles.progressBar}>
          <div style={{ ...styles.progressFill, width: `${successRate}%` }} />
        </div>
        <p style={styles.progressLabel}>전체 등록 완료율 {successRate}%</p>
      </section>

      {/* ── 플랫폼별 현황 ── */}
      <section style={styles.section}>
        <h2 style={styles.sectionTitle}>플랫폼별 등록 현황</h2>
        <table style={styles.table}>
          <thead>
            <tr>
              <th style={styles.th}>플랫폼</th>
              <th style={styles.th}>성공</th>
              <th style={styles.th}>실패</th>
              <th style={styles.th}>진행 중</th>
              <th style={styles.th}>성공률</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(s.platformStats).map(([platform, stat]) => {
              const total = stat.success + stat.failed;
              const rate = total > 0 ? Math.round((stat.success / total) * 100) : 0;
              return (
                <tr key={platform} style={styles.tr}>
                  <td style={styles.td}>{PLATFORM_LABEL[platform] ?? platform}</td>
                  <td style={{ ...styles.td, color: '#22c55e', fontWeight: 600 }}>{stat.success}</td>
                  <td style={{ ...styles.td, color: '#ef4444', fontWeight: 600 }}>{stat.failed}</td>
                  <td style={{ ...styles.td, color: '#f59e0b' }}>{stat.running}</td>
                  <td style={styles.td}>
                    <div style={styles.miniBar}>
                      <div style={{ ...styles.miniBarFill, width: `${rate}%` }} />
                    </div>
                    <span style={{ fontSize: 12 }}>{rate}%</span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </section>

      {/* ── Cafe24 크롤링 요청 ── */}
      <section style={styles.section}>
        <h2 style={styles.sectionTitle}>Cafe24 상품 수집</h2>
        <div style={styles.crawlRow}>
          <input
            style={styles.input}
            placeholder="https://yourshop.cafe24.com"
            value={shopUrl}
            onChange={(e) => setShopUrl(e.target.value)}
          />
          <button
            style={styles.btn}
            onClick={() => crawlMutation.mutate()}
            disabled={!shopUrl || crawlMutation.isPending}
          >
            {crawlMutation.isPending ? '요청 중...' : '크롤링 시작'}
          </button>
        </div>
        {crawlMsg && <p style={{ marginTop: 8, fontSize: 13, color: '#6366f1' }}>{crawlMsg}</p>}
      </section>
    </div>
  );
}

function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div style={{ ...styles.card, borderTop: `4px solid ${color}` }}>
      <div style={{ fontSize: 28, fontWeight: 700, color }}>{value.toLocaleString()}</div>
      <div style={{ fontSize: 13, color: '#64748b', marginTop: 4 }}>{label}</div>
    </div>
  );
}

// ── 인라인 스타일 ────────────────────────────────────────────────
const styles: Record<string, React.CSSProperties> = {
  page:          { padding: '24px 32px', maxWidth: 1100, margin: '0 auto', fontFamily: 'system-ui, sans-serif' },
  title:         { fontSize: 24, fontWeight: 700, marginBottom: 24, color: '#1e293b' },
  section:       { background: '#fff', borderRadius: 12, padding: 24, marginBottom: 24, boxShadow: '0 1px 4px rgba(0,0,0,.08)' },
  sectionTitle:  { fontSize: 16, fontWeight: 600, color: '#334155', marginBottom: 16, marginTop: 0 },
  cardGrid:      { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 16 },
  card:          { background: '#f8fafc', borderRadius: 10, padding: '16px 20px' },
  loading:       { padding: 48, textAlign: 'center', color: '#94a3b8' },
  progressBar:   { height: 8, background: '#e2e8f0', borderRadius: 4, marginTop: 20, overflow: 'hidden' },
  progressFill:  { height: '100%', background: '#22c55e', borderRadius: 4, transition: 'width .5s' },
  progressLabel: { fontSize: 13, color: '#64748b', marginTop: 6 },
  table:         { width: '100%', borderCollapse: 'collapse' },
  th:            { textAlign: 'left', padding: '10px 16px', fontSize: 13, color: '#64748b', borderBottom: '2px solid #e2e8f0' },
  tr:            { borderBottom: '1px solid #f1f5f9' },
  td:            { padding: '12px 16px', fontSize: 14 },
  miniBar:       { height: 6, background: '#e2e8f0', borderRadius: 3, width: 80, display: 'inline-block', verticalAlign: 'middle', marginRight: 6 },
  miniBarFill:   { height: '100%', background: '#22c55e', borderRadius: 3 },
  crawlRow:      { display: 'flex', gap: 12 },
  input:         { flex: 1, padding: '10px 14px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 14, outline: 'none' },
  btn:           { padding: '10px 20px', background: '#6366f1', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, cursor: 'pointer', fontWeight: 600 },
};
