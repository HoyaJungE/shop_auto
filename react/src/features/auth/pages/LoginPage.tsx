import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router-dom';
import { loginSchema, type LoginFormData } from '@/lib/validators/auth';
import { useLogin } from '../hooks/useLogin';

/**
 * 로그인 페이지
 *
 * [패턴]
 * - react-hook-form + Zod: 타입 안전한 폼 검증
 * - useLogin 훅: 로그인 mutation 캡슐화
 * - 에러 처리: form 레벨 에러 (서버 에러) + field 레벨 에러 (유효성)
 */
export default function LoginPage() {
  const navigate = useNavigate();
  const { mutate: login, isPending } = useLogin();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = (data: LoginFormData) => {
    login(data, {
      onSuccess: () => navigate('/dashboard'),
      onError: (error) => {
        setError('root', { message: error.message });
      },
    });
  };

  return (
    <div style={{ maxWidth: 400, margin: '100px auto', padding: '0 16px' }}>
      <h1>로그인</h1>

      <form onSubmit={handleSubmit(onSubmit)}>
        <div>
          <label htmlFor="email">이메일</label>
          <input id="email" type="email" {...register('email')} />
          {errors.email && <p style={{ color: 'red' }}>{errors.email.message}</p>}
        </div>

        <div>
          <label htmlFor="password">비밀번호</label>
          <input id="password" type="password" {...register('password')} />
          {errors.password && <p style={{ color: 'red' }}>{errors.password.message}</p>}
        </div>

        {errors.root && <p style={{ color: 'red' }}>{errors.root.message}</p>}

        <button type="submit" disabled={isPending}>
          {isPending ? '로그인 중...' : '로그인'}
        </button>
      </form>
    </div>
  );
}
