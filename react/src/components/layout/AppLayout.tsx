import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';

export default function AppLayout() {
  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: '#f8fafc' }}>
      <Sidebar />
      <main style={{ marginLeft: 220, flex: 1, minHeight: '100vh' }}>
        <Outlet />
      </main>
    </div>
  );
}
