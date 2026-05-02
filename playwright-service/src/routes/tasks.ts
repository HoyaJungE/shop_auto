import { Router, Request, Response } from 'express';
import { z } from 'zod';
import { registerQueue, crawlQueue } from '../queue/taskQueue';
import { Cafe24Crawler } from '../automation/cafe24';
import { CoupangAutomation } from '../automation/coupang';
import { NaverAutomation } from '../automation/naver';
import { OhouseAutomation } from '../automation/ohouse';
import { RegisterTask, CrawlRequest, Platform } from '../types';
import logger from '../logger';

const router = Router();

// ── 요청 스키마 검증 ──────────────────────────────────────────

const CredentialsSchema = z.object({
  loginId: z.string().min(1),
  password: z.string().min(1),
});

const ImageSchema = z.object({
  url: z.string().url(),
  order: z.number().int().min(0),
  type: z.enum(['REPRESENTATIVE', 'DETAIL']),
});

const OptionSchema = z.object({
  groupName: z.string(),
  value: z.string(),
  additionalPrice: z.number().default(0),
  stockQty: z.number().default(0),
});

const ProductPayloadSchema = z.object({
  cafe24ProductId: z.string(),
  name: z.string().min(1).max(100),
  originalPrice: z.number().int().min(0),
  salePrice: z.number().int().min(0),
  description: z.string().default(''),
  images: z.array(ImageSchema),
  options: z.array(OptionSchema),
  categoryCode: z.string(),
});

const RegisterTaskSchema = z.object({
  taskId: z.string().min(1),
  platform: z.enum(['COUPANG', 'NAVER', 'OHOUSE']),
  product: ProductPayloadSchema,
  credentials: CredentialsSchema,
});

const CrawlRequestSchema = z.object({
  taskId: z.string().min(1),
  credentials: CredentialsSchema.extend({ shopUrl: z.string().url() }),
  limit: z.number().int().positive().optional(),
});

// ── 헬스체크 ─────────────────────────────────────────────────

router.get('/health', (_req: Request, res: Response) => {
  res.json({
    status: 'ok',
    queue: {
      register: { size: registerQueue.size, running: registerQueue.activeCount },
      crawl:    { size: crawlQueue.size,    running: crawlQueue.activeCount },
    },
  });
});

// ── 상품 등록 API ─────────────────────────────────────────────

/**
 * POST /tasks/register
 * Spring Boot에서 상품 등록 작업을 요청한다.
 */
router.post('/register', async (req: Request, res: Response) => {
  const parsed = RegisterTaskSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ success: false, error: parsed.error.flatten() });
  }

  const task = parsed.data as RegisterTask;
  logger.info(`[API] 등록 작업 수신: ${task.taskId} / ${task.platform} / ${task.product.name}`);

  // 큐에 작업 추가 (비동기 — 즉시 응답)
  registerQueue
    .enqueue(task.taskId, () => runRegisterTask(task))
    .then((result) => {
      // 완료 후 Spring Boot로 콜백 (구성된 경우)
      notifySpringBoot(result).catch(() => {});
    })
    .catch((err) => {
      logger.error(`[Queue] 등록 작업 예외: ${task.taskId} — ${err.message}`);
    });

  return res.json({
    success: true,
    taskId: task.taskId,
    position: registerQueue.size,
    message: '등록 작업이 큐에 추가되었습니다.',
  });
});

// ── Cafe24 크롤링 API ─────────────────────────────────────────

/**
 * POST /tasks/crawl
 * Cafe24 관리자에서 상품을 크롤링한다.
 */
router.post('/crawl', async (req: Request, res: Response) => {
  const parsed = CrawlRequestSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ success: false, error: parsed.error.flatten() });
  }

  const crawlReq = parsed.data as CrawlRequest;
  logger.info(`[API] 크롤링 작업 수신: ${crawlReq.taskId}`);

  crawlQueue
    .enqueue(crawlReq.taskId, async () => {
      const crawler = new Cafe24Crawler();
      return crawler.crawl(crawlReq);
    })
    .then((result) => {
      notifySpringBoot(result).catch(() => {});
    })
    .catch((err) => {
      logger.error(`[Queue] 크롤링 작업 예외: ${crawlReq.taskId} — ${err.message}`);
    });

  return res.json({
    success: true,
    taskId: crawlReq.taskId,
    message: '크롤링 작업이 큐에 추가되었습니다.',
  });
});

// ── 큐 상태 조회 ─────────────────────────────────────────────

router.get('/queue/status', (_req: Request, res: Response) => {
  res.json({
    register: { size: registerQueue.size, running: registerQueue.activeCount },
    crawl: { size: crawlQueue.size, running: crawlQueue.activeCount },
  });
});

// ── 내부 함수 ─────────────────────────────────────────────────

async function runRegisterTask(task: RegisterTask) {
  switch (task.platform as Platform) {
    case 'COUPANG':
      return new CoupangAutomation().register(task);
    case 'NAVER':
      return new NaverAutomation().register(task);
    case 'OHOUSE':
      return new OhouseAutomation().register(task);
  }
}

async function notifySpringBoot(result: unknown): Promise<void> {
  const callbackUrl = process.env.SPRING_CALLBACK_URL;
  if (!callbackUrl) return;

  const res = await fetch(callbackUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(result),
  });
  if (!res.ok) {
    logger.warn(`[Callback] Spring Boot 콜백 실패: ${res.status}`);
  }
}

export default router;
