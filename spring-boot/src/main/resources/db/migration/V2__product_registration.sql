-- ════════════════════════════════════════════════════════════
-- V2: 상품 자동등록 스키마
-- Cafe24 → 쿠팡/네이버/오늘의집 자동등록 관련 테이블
-- ════════════════════════════════════════════════════════════

-- ── 상품 기본 정보 ─────────────────────────────────────────────
CREATE TABLE products (
    id                 BIGSERIAL PRIMARY KEY,
    cafe24_product_id  VARCHAR(50)  UNIQUE,
    name               VARCHAR(200) NOT NULL,
    original_price     INTEGER      DEFAULT 0,
    sale_price         INTEGER      NOT NULL,
    category_name      VARCHAR(100),                -- Cafe24 원본 카테고리명
    description        TEXT,                        -- 상세설명 (HTML)
    status             VARCHAR(20)  NOT NULL DEFAULT 'RAW',
    -- RAW      : Cafe24에서 수집만 된 상태
    -- READY    : 등록 준비 완료 (카테고리 매핑 완료)
    -- PUBLISHING: 등록 진행 중
    -- DONE     : 모든 플랫폼 등록 완료
    -- ERROR    : 오류 발생
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_status        ON products(status);
CREATE INDEX idx_products_cafe24_id     ON products(cafe24_product_id);

-- ── 상품 이미지 ───────────────────────────────────────────────
CREATE TABLE product_images (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url   VARCHAR(1000) NOT NULL,
    image_order INTEGER      NOT NULL DEFAULT 0,
    image_type  VARCHAR(20)  NOT NULL DEFAULT 'DETAIL',
    -- REPRESENTATIVE : 대표 이미지
    -- DETAIL         : 추가 이미지
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_images_product_id ON product_images(product_id);

-- ── 상품 옵션 ─────────────────────────────────────────────────
CREATE TABLE product_options (
    id               BIGSERIAL PRIMARY KEY,
    product_id       BIGINT      NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    option_group     VARCHAR(50) NOT NULL,   -- 예: 색상, 사이즈
    option_value     VARCHAR(100) NOT NULL,  -- 예: 아이보리, SS
    stock_qty        INTEGER     NOT NULL DEFAULT 0,
    additional_price INTEGER     NOT NULL DEFAULT 0,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_options_product_id ON product_options(product_id);

-- ── 플랫폼 등록 이력 ──────────────────────────────────────────
CREATE TABLE platform_registrations (
    id                  BIGSERIAL PRIMARY KEY,
    product_id          BIGINT      NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    platform            VARCHAR(20) NOT NULL,   -- COUPANG / NAVER / OHOUSE
    task_id             VARCHAR(100),           -- playwright-service 작업 ID
    platform_product_id VARCHAR(100),           -- 플랫폼 측 부여 상품 ID
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING    : 등록 대기
    -- RUNNING    : 등록 진행 중
    -- SUCCESS    : 등록 완료
    -- FAILED     : 등록 실패
    error_message       TEXT,
    screenshot_path     VARCHAR(500),           -- 실패 시 스크린샷 경로
    registered_at       TIMESTAMP,
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, platform)               -- 플랫폼당 1건
);

CREATE INDEX idx_platform_reg_product_id ON platform_registrations(product_id);
CREATE INDEX idx_platform_reg_status     ON platform_registrations(status);
CREATE INDEX idx_platform_reg_platform   ON platform_registrations(platform);

-- ── 카테고리 매핑 ─────────────────────────────────────────────
-- Cafe24 카테고리명 → 각 플랫폼 카테고리 코드 매핑 테이블
-- Claude AI가 초기 매핑을 제안하고, 관리자가 확인/수정한다.
CREATE TABLE category_mappings (
    id                      BIGSERIAL PRIMARY KEY,
    cafe24_category         VARCHAR(100) NOT NULL,
    platform                VARCHAR(20)  NOT NULL,  -- COUPANG / NAVER / OHOUSE
    platform_category_id    VARCHAR(100) NOT NULL,
    platform_category_name  VARCHAR(200),
    confirmed               BOOLEAN      NOT NULL DEFAULT FALSE,  -- 관리자 확인 여부
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (cafe24_category, platform)
);

CREATE INDEX idx_category_mappings_cafe24    ON category_mappings(cafe24_category);
CREATE INDEX idx_category_mappings_confirmed ON category_mappings(confirmed);

-- ── 플랫폼 자격증명 (암호화 저장) ────────────────────────────
-- ⚠️ 실제 운영에서는 Vault나 AWS Secrets Manager 사용 권장
-- 여기서는 application.yml의 jasypt 암호화로 처리
CREATE TABLE platform_credentials (
    id          BIGSERIAL PRIMARY KEY,
    platform    VARCHAR(20)  NOT NULL UNIQUE,  -- COUPANG / NAVER / OHOUSE / CAFE24
    login_id    VARCHAR(200) NOT NULL,
    password    VARCHAR(500) NOT NULL,          -- 암호화 저장
    extra       JSONB,                          -- 추가 정보 (shopUrl 등)
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── updated_at 자동 갱신 트리거 ──────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_platform_reg_updated_at
    BEFORE UPDATE ON platform_registrations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_category_mappings_updated_at
    BEFORE UPDATE ON category_mappings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_platform_credentials_updated_at
    BEFORE UPDATE ON platform_credentials
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
