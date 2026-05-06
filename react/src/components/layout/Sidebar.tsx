import { NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';

const NAV_ITEMS = [
  { to: '/dashboard', icon: '📊', label: '대시보드' },
  { to: '/products',  icon: '📦', label: '상품 목록' },
];

export default function Sidebar() {
  const navigate = useNavigate();
  const clearAuth = useAuthStore((s) => s.clearAuth);

  const handleLogout = () => {
    clearAuth();
    navigate('/login');
  };

  return (
    <aside style={styles.sidebar}>
      <div style={styles.logo}>🛒 Harness</div>

      <nav style={styles.nav}>
        {NAV_ITEMS.map(({ to, icon, label }) => (
          <NavLink
            key={to}
            to={to}
            style={({ isActive }) => ({
              ...styles.navItem,
              ...(isActive ? styles.navItemActive : {}),
            })}
          >
            <span style={{ marginRight: 10 }}>{icon}</span>
            {label}
          </NavLink>
        ))}
      </nav>

      <button style={styles.logoutBtn} onClick={handleLogout}>
        🚪 로그아웃
      </button>
    </aside>
  );
}

const styles: Record<string, React.CSSProperties> = {
  sidebar:       { width: 220, minHeight: '100vh', background: '#1e293b', display: 'flex', flexDirection: 'column', padding: '24px 0', position: 'fixed', top: 0, left: 0 },
  logo:          { fontSize: 20, fontWeight: 700, color: '#f1f5f9', padding: '0 20px 24px', borderBottom: '1px solid #334155' },
  nav:           { flex: 1, padding: '16px 0' },
  navItem:       { display: 'flex', alignItems: 'center', padding: '10px 20px', color: '#94a3b8', textDecoration: 'none', fontSize: 14, fontWeight: 500, transition: 'background .15s' },
  navItemActive: { background: '#334155', color: '#f1f5f9', borderLeft: '3px solid #6366f1' },
  logoutBtn:     { margin: '16px', padding: '10px', background: 'transparent', border: '1px solid #334155', color: '#94a3b8', borderRadius: 8, cursor: 'pointer', fontSize: 13 },
};
