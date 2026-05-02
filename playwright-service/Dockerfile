FROM mcr.microsoft.com/playwright:v1.49.0-jammy

WORKDIR /app

# 의존성 설치
COPY package*.json ./
RUN npm ci

# 소스 복사 및 빌드
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# Playwright 브라우저 설치 (Chromium만)
RUN npx playwright install chromium --with-deps

# 로그/스크린샷 디렉토리
RUN mkdir -p logs screenshots

EXPOSE 3100

CMD ["node", "dist/index.js"]
