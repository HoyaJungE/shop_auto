import express from 'express';
import tasksRouter from './routes/tasks';
import logger from './logger';
import fs from 'fs';

// ── 디렉토리 초기화 ───────────────────────────────────────────
['logs', 'screenshots'].forEach((dir) => {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
});

const app = express();
const PORT = parseInt(process.env.PORT ?? '3100');

// ── 미들웨어 ──────────────────────────────────────────────────
app.use(express.json({ limit: '10mb' }));

// 요청 로깅
app.use((req, _res, next) => {
  logger.info(`${req.method} ${req.path}`);
  next();
});

// ── 라우터 ────────────────────────────────────────────────────
app.use('/tasks', tasksRouter);

// ── 에러 핸들러 ───────────────────────────────────────────────
app.use((err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  logger.error(`Unhandled error: ${err.message}`, err);
  res.status(500).json({ success: false, error: err.message });
});

// ── 서버 시작 ─────────────────────────────────────────────────
app.listen(PORT, '0.0.0.0', () => {
  logger.info(`playwright-service 시작 — port ${PORT}`);
  logger.info(`BROWSER_HEADLESS: ${process.env.BROWSER_HEADLESS ?? 'true'}`);
  logger.info(`BROWSER_SLOW_MO: ${process.env.BROWSER_SLOW_MO ?? '200'}ms`);
});

export default app;
