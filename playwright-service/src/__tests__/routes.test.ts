import { describe, it, expect, vi, beforeAll, afterAll } from 'vitest';
import express from 'express';
import request from 'supertest';
import tasksRouter from '../routes/tasks';

// playwright 자동화 클래스 전체 mock (실제 브라우저 안 띄움)
vi.mock('../automation/cafe24', () => ({
  Cafe24Crawler: vi.fn().mockImplementation(() => ({
    crawl: vi.fn().mockResolvedValue({
      taskId: 'crawl-task-1',
      status: 'SUCCESS',
      products: [{ cafe24ProductId: 'P001', name: '테스트 이불', salePrice: 50000 }],
    }),
  })),
}));

vi.mock('../automation/coupang', () => ({
  CoupangAutomation: vi.fn().mockImplementation(() => ({
    register: vi.fn().mockResolvedValue({
      taskId: 'task-1',
      status: 'SUCCESS',
      platformProductId: 'CP12345',
      completedAt: new Date().toISOString(),
    }),
  })),
}));

vi.mock('../automation/naver', () => ({
  NaverAutomation: vi.fn().mockImplementation(() => ({
    register: vi.fn().mockResolvedValue({
      taskId: 'task-2',
      status: 'SUCCESS',
      platformProductId: 'NV67890',
      completedAt: new Date().toISOString(),
    }),
  })),
}));

vi.mock('../automation/ohouse', () => ({
  OhouseAutomation: vi.fn().mockImplementation(() => ({
    register: vi.fn().mockResolvedValue({
      taskId: 'task-3',
      status: 'SUCCESS',
      completedAt: new Date().toISOString(),
    }),
  })),
}));

const app = express();
app.use(express.json());
app.use('/tasks', tasksRouter);

// ── 테스트 픽스처 ─────────────────────────────────────────────
const validRegisterBody = {
  taskId: 'test-task-001',
  platform: 'COUPANG',
  product: {
    cafe24ProductId: 'P001',
    name: '프리미엄 구스다운 이불',
    originalPrice: 79000,
    salePrice: 59000,
    description: '<p>상세설명</p>',
    images: [{ url: 'https://example.com/img.jpg', order: 0, type: 'REPRESENTATIVE' }],
    options: [{ groupName: '색상', value: '아이보리', additionalPrice: 0, stockQty: 50 }],
    categoryCode: '10000123',
  },
  credentials: { loginId: 'test@test.com', password: 'pass1234' },
};

const validCrawlBody = {
  taskId: 'crawl-001',
  credentials: {
    loginId: 'admin@cafe24.com',
    password: 'pass1234',
    shopUrl: 'https://admin.cafe24.com',
  },
  limit: 10,
};

// ── 헬스체크 ─────────────────────────────────────────────────
describe('GET /tasks/health', () => {
  it('200과 큐 상태를 반환한다', async () => {
    const res = await request(app).get('/tasks/health');
    expect(res.status).toBe(200);
    expect(res.body).toHaveProperty('status', 'ok');
    expect(res.body).toHaveProperty('queue');
    expect(res.body.queue).toHaveProperty('register');
    expect(res.body.queue).toHaveProperty('crawl');
  });
});

// ── 등록 API ─────────────────────────────────────────────────
describe('POST /tasks/register', () => {
  it('유효한 요청 → 200 + taskId 반환', async () => {
    const res = await request(app)
      .post('/tasks/register')
      .send(validRegisterBody);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.taskId).toBe('test-task-001');
    expect(res.body).toHaveProperty('position');
  });

  it('필수 필드 누락 → 400 반환', async () => {
    const res = await request(app)
      .post('/tasks/register')
      .send({ taskId: 'only-id' });

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
  });

  it('잘못된 platform 값 → 400 반환', async () => {
    const res = await request(app)
      .post('/tasks/register')
      .send({ ...validRegisterBody, platform: 'INVALID_PLATFORM' });

    expect(res.status).toBe(400);
  });

  it('이미지 URL이 유효하지 않으면 → 400 반환', async () => {
    const body = {
      ...validRegisterBody,
      product: {
        ...validRegisterBody.product,
        images: [{ url: 'not-a-url', order: 0, type: 'REPRESENTATIVE' }],
      },
    };
    const res = await request(app).post('/tasks/register').send(body);
    expect(res.status).toBe(400);
  });

  it('NAVER 플랫폼도 처리한다', async () => {
    const res = await request(app)
      .post('/tasks/register')
      .send({ ...validRegisterBody, taskId: 'naver-task-1', platform: 'NAVER' });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });

  it('OHOUSE 플랫폼도 처리한다', async () => {
    const res = await request(app)
      .post('/tasks/register')
      .send({ ...validRegisterBody, taskId: 'ohouse-task-1', platform: 'OHOUSE' });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });
});

// ── 크롤링 API ────────────────────────────────────────────────
describe('POST /tasks/crawl', () => {
  it('유효한 크롤링 요청 → 200 반환', async () => {
    const res = await request(app)
      .post('/tasks/crawl')
      .send(validCrawlBody);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.taskId).toBe('crawl-001');
  });

  it('shopUrl 없으면 → 400 반환', async () => {
    const res = await request(app)
      .post('/tasks/crawl')
      .send({
        taskId: 'crawl-002',
        credentials: { loginId: 'a', password: 'b' },
      });

    expect(res.status).toBe(400);
  });
});

// ── 큐 상태 API ───────────────────────────────────────────────
describe('GET /tasks/queue/status', () => {
  it('큐 상태를 반환한다', async () => {
    const res = await request(app).get('/tasks/queue/status');
    expect(res.status).toBe(200);
    expect(res.body).toHaveProperty('register');
    expect(res.body).toHaveProperty('crawl');
  });
});
