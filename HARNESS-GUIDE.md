# 프로젝트 하네스 가이드

새 프로젝트 시작 시 이 하네스를 복사해서 사용한다.

---

## 폴더 구조

```
harness/
├── spring-boot/                       # 백엔드 하네스
│   ├── build.gradle
│   └── src/main/java/com/harness/
│       ├── config/
│       │   ├── SecurityConfig.java    ── CORS, JWT 필터, 공개URL 설정
│       │   └── SwaggerConfig.java     ── OpenAPI/Swagger 설정
│       ├── domain/
│       │   └── BaseEntity.java        ── id, createdAt, updatedAt 공통 엔티티
│       ├── dto/
│       │   └── ApiResponse.java       ── 공통 응답 래퍼
│       ├── exception/
│       │   ├── BusinessException.java ── 비즈니스 예외
│       │   ├── ErrorCode.java         ── 에러 코드 enum
│       │   └── GlobalExceptionHandler.java ── 전역 예외 핸들러
│       └── security/
│           ├── JwtProvider.java       ── JWT 발급/검증
│           └── JwtAuthenticationFilter.java ── Security 필터
│
└── react/                             # 프론트엔드 하네스
    ├── package.json
    ├── .env.example
    └── src/
        ├── api/
        │   ├── axiosInstance.ts       ── Axios 공통 인스턴스 (토큰 주입, 갱신)
        │   └── authApi.ts             ── 인증 API 예시
        ├── components/common/
        │   └── ProtectedRoute.tsx     ── 인증 보호 라우트
        ├── hooks/
        │   └── useApiError.ts         ── API 에러 메시지 추출 훅
        ├── store/
        │   └── authStore.ts           ── Zustand 인증 상태
        ├── types/
        │   └── api.ts                 ── ApiResponse, PageResponse 타입
        └── App.tsx                    ── 라우터 + QueryClient 설정
```

---

## 새 프로젝트 시작 체크리스트

### Spring Boot

- [ ] `build.gradle`에서 `group`, `version` 수정
- [ ] `application.yml`의 `spring.application.name` 수정
- [ ] 패키지 `com.harness` → 실제 패키지명으로 일괄 변경
- [ ] `SecurityConfig.java`의 `PUBLIC_URLS`에 공개 엔드포인트 추가
- [ ] `SecurityConfig.java`의 CORS 허용 origin 수정
- [ ] `ErrorCode.java`에 도메인별 에러 코드 추가
- [ ] `BaseEntity`를 상속하는 도메인 Entity 작성
- [ ] `src/main/resources/db/migration/` 에 Flyway SQL 파일 추가
- [ ] `.env` 또는 환경변수로 `DB_*`, `JWT_SECRET` 주입
- [ ] `@EnableJpaAuditing` 을 메인 Application 클래스에 추가

### React

- [ ] `package.json`의 `name` 수정 후 `npm install`
- [ ] `.env.example` → `.env.local` 복사 후 `VITE_API_BASE_URL` 설정
- [ ] `.gitignore`에 `.env.local` 추가 확인
- [ ] `src/pages/` 에 실제 페이지 컴포넌트 추가
- [ ] `App.tsx` 라우트 테이블에 실제 경로 등록
- [ ] `src/api/` 에 도메인별 API 파일 추가 (authApi.ts 참고)
- [ ] `authStore.ts`의 persist key (`auth-storage`) 프로젝트별로 변경

---

## 핵심 패턴 레퍼런스

### Spring: 새 도메인 추가 순서

```
1. Domain (Entity)     extends BaseEntity
2. Repository          extends JpaRepository<Entity, Long>
3. Service             비즈니스 로직, 예외는 throw new BusinessException(ErrorCode.XXX)
4. Controller          @RestController, 반환은 ResponseEntity<ApiResponse<T>>
5. ErrorCode           새 에러 코드 추가
```

### Spring: Controller 반환 예시

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long id) {
    UserResponse user = userService.findById(id); // 없으면 BusinessException
    return ResponseEntity.ok(ApiResponse.ok(user));
}
```

### React: 새 API 함수 추가 패턴

```typescript
// src/api/userApi.ts
export const getUser = async (id: number): Promise<UserResponse> => {
  const { data } = await apiClient.get<ApiResponse<UserResponse>>(`/api/v1/users/${id}`);
  if (!data.success || !data.data) throw new Error(data.error?.message);
  return data.data;
};
```

### React: React Query 사용 패턴

```typescript
// 조회
const { data, isLoading } = useQuery({
  queryKey: ['user', id],
  queryFn: () => getUser(id),
});

// 변경 (POST/PUT/DELETE)
const mutation = useMutation({
  mutationFn: updateUser,
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user'] }),
  onError: (error) => alert(getErrorMessage(error)),
});
```

---

## 의존성 버전 (2026년 기준)

| 항목 | 버전 |
|------|------|
| Java | 21 (LTS) |
| Spring Boot | 3.3.x |
| jjwt | 0.12.x |
| springdoc-openapi | 2.6.x |
| React | 18.3.x |
| Vite | 5.x |
| TanStack Query | 5.x |
| Zustand | 5.x |
| Axios | 1.7.x |

---

## 주의 사항

- `JWT_SECRET`은 반드시 256bit(32자) 이상 길이의 랜덤 문자열 사용
- `application.yml`의 `ddl-auto`는 prod에서 반드시 `validate`
- Flyway 마이그레이션 파일명 규칙: `V{버전}__{설명}.sql` (예: `V1__init_schema.sql`)
- accessToken은 localStorage 대신 메모리 관리를 고려 (XSS 위험 trade-off 판단 필요)
