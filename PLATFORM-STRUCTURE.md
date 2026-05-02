# 자사몰 상품 자동등록 — 플랫폼 구조 분석

> 분석일: 2026-05-02  
> 대상 플랫폼: 쿠팡 WING, 네이버 스마트스토어, 오늘의집  
> 상품 유형: 침구류 (100개 미만)  
> 우선순위: 쿠팡 → 네이버 → 오늘의집

---

## 1. 쿠팡 WING

### 1-1. 등록 방식

| 방식 | 경로 | 자동화 난이도 |
|------|------|--------------|
| **Open API** | `developers.coupangcorp.com` | ★★★★☆ (추천) |
| **엑셀 일괄등록** | WING > 상품관리 > 상품 일괄등록 | ★★★☆☆ |
| **단건 등록** | WING > 상품등록 | 자동화 부적합 |

### 1-2. Open API 구조

**인증**
```
HMAC-SHA256 서명 방식
- AccessKey + SecretKey 발급 (WING 판매자센터 > 개발자 센터)
- Authorization: CEA algorithm=HmacSHA256, access-key={key}, signed-date={ts}, signature={hmac}
```

**상품 등록 엔드포인트**
```
POST /v2/providers/seller_api/apis/api/v1/marketplace/seller-products
```

**필수 필드 구조 (침구류 기준)**
```json
{
  "displayCategoryCode": 12345,          // 카테고리 코드 (침구류)
  "sellerProductName": "상품명",          // 필수
  "vendorId": "A00012345",               // 판매자 ID
  "salePrice": 59000,                    // 판매가 (필수)
  "contents": "<p>상세설명 HTML</p>",     // 필수
  "deliveryMethod": "PARCEL",            // 택배/방문수령 등
  "deliveryCompanyCode": "KGB",          // 배송사 코드
  "deliveryChargeType": "FREE",          // 무료/유료
  "returnCenterCode": "return_center_id",// 반품센터 코드 (필수)
  "items": [
    {
      "itemName": "옵션명",               // 구매옵션명 (침구류: 색상/사이즈)
      "originalPrice": 79000,            // 정가
      "salePrice": 59000,                // 판매가
      "maximumBuyCount": 10,
      "maximumBuyForPerson": 10,
      "attributes": [
        { "attributeTypeName": "색상", "attributeValueName": "아이보리" },
        { "attributeTypeName": "사이즈", "attributeValueName": "SS" }
      ],
      "images": [
        { "imageOrder": 0, "imageType": "REPRESENTATION", "cdnPath": "https://..." },
        { "imageOrder": 1, "imageType": "DETAIL", "cdnPath": "https://..." }
      ]
    }
  ]
}
```

### 1-3. 엑셀 일괄등록 구조 (API 불가 시 대안)

**다운로드 경로**: WING > 상품관리 > 상품 일괄등록 > 카테고리 선택 > 엑셀 양식 다운로드

> ⚠️ **2025년 11월 28일부터 신규 엑셀 형식 의무화** — 반드시 최신 파일 사용

**엑셀 주요 시트**
- Sheet1: 상품 기본 정보
- Sheet2: 구매옵션 (12개 항목 — 구버전 8개에서 확장)
- Sheet3: 이미지 URL 목록
- Row 4: 각 컬럼별 입력 방법 안내

**침구류 필수 컬럼**
```
[기본]
- 상품명 (최대 100자)
- 카테고리 코드
- 정가 / 판매가
- 재고수량

[구매옵션 — 침구류]
- 구매옵션명1: 색상 (아이보리, 그레이, 네이비 등)
- 구매옵션명2: 사이즈 (SS, S, Q, K, SQ 등)
- 구매옵션값1 / 구매옵션값2

[상세]
- 대표이미지 URL
- 추가이미지 URL (최대 9장)
- 상세설명 (HTML 허용)
- KC 인증정보 (침구류 필수)
- 제조사 / 브랜드

[배송]
- 배송방법 (택배)
- 배송비 유형 (무료/조건부)
- 출고지 / 반품지 코드
```

### 1-4. 카테고리 코드 (침구류)

```
홈/리빙 > 침구/베딩 > ...
- 이불: 약 10000XXX 대
- 베개: 약 10000XXX 대
- 침대커버: 약 10000XXX 대
```

> 정확한 코드는 Open API로 조회:  
> `GET /v2/providers/seller_api/apis/api/v1/marketplace/meta/display-categories/{displayCategoryCode}/attributes`

---

## 2. 네이버 스마트스토어

### 2-1. 등록 방식

| 방식 | 경로 | 자동화 난이도 |
|------|------|--------------|
| **Commerce API (공식)** | `api.commerce.naver.com` | ★★★★☆ (추천) |
| **엑셀 일괄등록** | 스마트스토어센터 > 상품관리 > 상품 일괄등록 | ★★★☆☆ |

### 2-2. Commerce API 구조

**인증**
```
OAuth 2.0 Client Credentials
POST https://api.commerce.naver.com/external/v1/oauth2/token
  grant_type=client_credentials
  client_id={CLIENT_ID}
  client_secret={CLIENT_SECRET}
  type=SELF (자사몰)
```

**상품 등록 엔드포인트**
```
POST /external/v2/products
```

**핵심 Request Body 구조**
```json
{
  "originProduct": {
    "statusType": "SALE",                 // 판매중 (필수)
    "saleType": "NEW",                    // 새상품 (필수)
    "leafCategoryId": "50001234",         // 리프 카테고리 ID (필수)
    "name": "상품명",                      // 필수 (최대 100자)
    "images": {
      "representativeImage": { "url": "https://..." },  // 필수
      "optionalImages": [{ "url": "https://..." }]
    },
    "detailContent": "<p>상세설명</p>",   // 필수 (HTML)
    "salePrice": 59000,                  // 판매가 (필수)
    "stockQuantity": 999,                // 재고수량
    "deliveryInfo": {
      "deliveryType": "DELIVERY",        // 배송 방법 (필수)
      "deliveryAttributeType": "NORMAL", // 일반 배송 (필수)
      "deliveryCompany": "CJGLS",        // 배송사
      "deliveryFee": {
        "deliveryFeeType": "FREE"        // 무료배송
      }
    },
    "detailAttribute": {
      "afterServiceInfo": {
        "afterServiceTelephoneNumber": "0000-0000",
        "afterServiceGuideContent": "AS 안내 내용"
      },
      "originAreaInfo": {
        "originAreaCode": "KOREA"        // 원산지
      },
      "optionInfo": {
        "optionCombinationGroupNames": {
          "optionGroupName1": "색상",
          "optionGroupName2": "사이즈"
        },
        "optionCombinations": [
          {
            "id": 1,
            "optionName1": "아이보리",
            "optionName2": "SS",
            "stockQuantity": 100,
            "price": 0,
            "usable": true
          }
        ]
      }
    }
  },
  "smartStoreChannelProduct": {
    "naverShoppingRegistration": true,   // 네이버쇼핑 등록 여부
    "channelProductDisplayStatusType": "ON"
  }
}
```

### 2-3. 카테고리 조회

```
GET /external/v1/categories/root-categories    // 루트 카테고리 목록
GET /external/v1/categories/{id}/child-categories  // 하위 카테고리
```

---

## 3. 오늘의집

### 3-1. 등록 방식

| 방식 | 설명 | 자동화 난이도 |
|------|------|--------------|
| **파트너센터 웹 폼 (오로라)** | `partnerbucketplace.com` | ★★☆☆☆ (Playwright) |
| **엑셀 일괄업로드** | 파트너센터 내 엑셀 업로드 기능 | ★★★☆☆ |
| **공개 API** | **없음** | — |

> ⚠️ 오늘의집은 공개 API를 제공하지 않는다. Playwright 자동화 또는 엑셀 업로드가 유일한 대안.

### 3-2. 파트너센터 구조 (오로라 시스템)

**접속 경로**: `https://www.partnerbucketplace.com`

**상품 등록 흐름 (오로라)**
```
파트너센터 로그인
  → 상품관리 > 신상품 등록
  → 카테고리 선택 (최하위 카테고리명 검색/선택)
  → 기본 정보 입력
    ├── 상품명
    ├── 판매가 / 정가
    ├── 대표이미지 (최소 1장)
    ├── 추가이미지 (최대 10장)
    ├── 상세설명 (HTML 에디터 또는 이미지 업로드)
    ├── 옵션 설정 (색상/사이즈 조합)
    └── 배송비 정책
  → 임시저장 또는 등록 요청
  → 심사 후 게시 (일반 판매자: 심사 필요)
```

**침구류 카테고리 경로**
```
홈 > 침실 > 침구
  ├── 이불/베딩세트
  ├── 베개/쿠션
  ├── 침대커버/패드
  └── 이불솜/베개솜
```

### 3-3. 엑셀 일괄업로드 구조

```
파트너센터 > 상품관리 > 상품 일괄업로드
  → 카테고리 선택 후 엑셀 양식 다운로드
  → 작성 후 업로드
```

**주요 컬럼**
```
- 카테고리 코드 (필수)
- 상품명 (필수)
- 판매가 / 정가
- 대표이미지 URL
- 추가이미지 URL
- 상세설명 HTML 또는 이미지 URL
- 옵션명 / 옵션값
- 옵션별 가격 추가금
- 배송비 정책
- 브랜드/제조사
- 원산지
```

---

## 4. 자동화 전략 종합

### 4-1. 플랫폼별 접근법

```
Cafe24 관리자 (Playwright)
  → 상품 목록 크롤링 → DB 저장
       │
       ├─→ [쿠팡] Open API POST /seller-products
       │         HMAC 서명 + JSON Body
       │
       ├─→ [네이버] Commerce API POST /products
       │         OAuth 2.0 + JSON Body
       │
       └─→ [오늘의집] Playwright 폼 자동화
                    또는 엑셀 생성 후 수동 업로드
```

### 4-2. DB 공통 상품 스키마 (제안)

```sql
CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    cafe24_product_id VARCHAR(50) UNIQUE,
    name            VARCHAR(200) NOT NULL,
    original_price  INTEGER,
    sale_price      INTEGER NOT NULL,
    category_name   VARCHAR(100),  -- Cafe24 카테고리명
    description     TEXT,          -- HTML
    status          VARCHAR(20) DEFAULT 'RAW',  -- RAW/READY/PUBLISHING/DONE/ERROR
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE product_images (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT REFERENCES products(id),
    image_url   VARCHAR(500),
    image_order INTEGER,
    image_type  VARCHAR(20)  -- REPRESENTATIVE/DETAIL
);

CREATE TABLE product_options (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT REFERENCES products(id),
    option_group VARCHAR(50),  -- 색상/사이즈
    option_value VARCHAR(100),
    stock_qty    INTEGER DEFAULT 0,
    additional_price INTEGER DEFAULT 0
);

CREATE TABLE platform_registrations (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT REFERENCES products(id),
    platform        VARCHAR(20) NOT NULL,  -- COUPANG/NAVER/OHOUSE
    platform_product_id VARCHAR(100),      -- 플랫폼 측 상품 ID
    status          VARCHAR(20),           -- PENDING/SUCCESS/FAILED
    error_message   TEXT,
    registered_at   TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE category_mappings (
    id              BIGSERIAL PRIMARY KEY,
    cafe24_category VARCHAR(100),
    platform        VARCHAR(20),
    platform_category_id VARCHAR(100),
    platform_category_name VARCHAR(200),
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(cafe24_category, platform)
);
```

### 4-3. 구현 우선순위

```
Phase 1 — 쿠팡 자동화 (2~3주)
  ① Cafe24 관리자 Playwright 크롤러 구현
  ② DB 스키마 마이그레이션 (Flyway)
  ③ 쿠팡 Open API 클라이언트 (HMAC 서명 포함)
  ④ 카테고리 매핑 관리 (Claude AI 지원)
  ⑤ WPF → Spring Boot API 호출로 상품 목록 조회/등록 트리거

Phase 2 — 네이버 스마트스토어 (1~2주)
  ① Naver Commerce API OAuth 2.0 클라이언트
  ② 스마트스토어 전용 필드 매핑
  ③ 기존 DB 재활용

Phase 3 — 오늘의집 (2~3주)
  ① Playwright 파트너센터 자동화
  ② 엑셀 생성 fallback 로직
```

### 4-4. 핵심 리스크

| 리스크 | 대응 |
|--------|------|
| Cafe24 관리자 UI 변경 | Playwright selector에 data-* 속성 우선 사용 + 오류 시 알림 |
| 쿠팡 API Rate Limit | 초당 요청 제한 준수 (공식: 5 req/s) + Retry with backoff |
| 오늘의집 Playwright 탐지 | User-Agent 설정, 요청 간격 조절, 쿠키 유지 |
| 카테고리 불일치 | Claude AI로 제안 + 관리자 확인 UI 제공 |
| 이미지 URL 만료 | Cafe24 이미지를 MinIO에 별도 저장 후 퍼블릭 URL 사용 |

---

## 5. 참고 링크

- [쿠팡 Open API 공식 문서](https://developers.coupangcorp.com/hc/en-us/articles/360033917473)
- [쿠팡 상품 등록 API](https://developers.coupangcorp.com/hc/en-us/articles/360033877853)
- [쿠팡 엑셀 일괄등록 가이드](https://marketplace.coupang.com/mba-01/mba-4-3)
- [네이버 커머스 API GitHub](https://github.com/commerce-api-naver/commerce-api)
- [오늘의집 파트너센터 가이드](https://www.partnerbucketplace.com/hc/ko/articles/20536345084185)
- [오늘의집 오로라 신상품 등록 가이드](https://www.partnerbucketplace.com/hc/ko/articles/25500904475545)
