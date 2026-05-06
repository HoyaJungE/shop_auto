import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router-dom';
import { loginSchema, type LoginFormData } from '@/lib/validators/auth';
import { useLogin } from '../hooks/useLogin';

export default function LoginPage() {
  const navigate = useNavigate();
  const { mutate: login, isPending } = useLogin();

  const { register, handleSubmit, setError, formState: { errors } } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = (data: LoginFormData) => {
    login(data, {
      onSuccess: () => navigate('/dashboard'),
      onError: (error) => setError('root', { message: error.message }),
    });
  };

  return (
    <div style={styles.bg}>
      <div style={styles.card}>
        {/* 로고 */}
        <div style={styles.logoArea}>
          <span style={styles.logoIcon}>🛒</span>
          <h1 style={styles.logoText}>Harness</h1>
          <p style={styles.subtitle}>상품 자동등록 관리 시스템</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} style={styles.form}>
          {/* 이메일 */}
          <div style={styles.fieldWrap}>
            <label style={styles.label}>이메일</label>
            <input
              id="email"
              type="email"
              placeholder="admin@example.com"
              style={{ ...styles.input, ...(errors.email ? styles.inputError : {}) }}
              {...register('email')}
            />
            {errors.email && <p style={styles.errMsg}>{errors.email.message}</p>}
          </div>

          {/* 비밀번호 */}
          <div style={styles.fieldWrap}>
            <label style={styles.label}>비밀번호</label>
            <input
              id="password"
              type="password"
              placeholder="••••••••"
              style={{ ...styles.input, ...(errors.password ? styles.inputError : {}) }}
              {...register('password')}
            />
            {errors.password && <p style={styles.errMsg}>{errors.password.message}</p>}
          </div>

          {/* 서버 에러 */}
          {errors.root && (
            <div style={styles.alert}>{errors.root.message}</div>
          )}

          {/* 제출 */}
          <button type="submit" disabled={isPending} style={styles.submitBtn}>
            {isPending ? '로그인 중...' : '로그인'}
          </button>
        </form>
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  bg:         { minHeight: '100vh', background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 },
  card:       { background: '#fff', borderRadius: 16, padding: '40px 36px', width: '100%', maxWidth: 400, boxShadow: '0 20px 60px rgba(0,0,0,.15)' },
  logoArea:   { textAlign: 'center', marginBottom: 32 },
  logoIcon:   { fontSize: 40 },
  logoText:   { fontSize: 26, fontWeight: 700, color: '#1e293b', margin: '8px 0 4px', display: 'block' },
  subtitle:   { fontSize: 13, color: '#94a3b8', margin: 0 },
  form:       { display: 'flex', flexDirection: 'column', gap: 16 },
  fieldWrap:  { display: 'flex', flexDirection: 'column', gap: 6 },
  label:      { fontSize: 13, fontWeight: 600, color: '#475569' },
  input:      { padding: '11px 14px', border: '1px solid #e2e8f0', borderRadius: 8, fontSize: 14, outline: 'none', transition: 'border-color .15s', fontFamily: 'inherit' },
  inputError: { borderColor: '#ef4444' },
  errMsg:     { fontSize: 12, color: '#ef4444', margin: 0 },
  alert:      { background: '#fee2e2', color: '#b91c1c', padding: '10px 14px', borderRadius: 8, fontSize: 13 },
  submitBtn:  { padding: '12px', background: 'linear-gradient(135deg, #667eea, #764ba2)', color: '#fff', border: 'none', borderRadius: 8, fontSize: 15, fontWeight: 700, cursor: 'pointer', marginTop: 8, transition: 'opacity .15s' },
};
