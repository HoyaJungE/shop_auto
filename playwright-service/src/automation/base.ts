import { Browser, BrowserContext, Page, chromium } from 'playwright';
import logger from '../logger';

export interface BrowserOptions {
  headless?: boolean;
  slowMo?: number;
  userAgent?: string;
}

const DEFAULT_USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';

/**
 * 브라우저 자동화 공통 기반 클래스
 *
 * [개선사항]
 * - withRetry: 일시적 오류에 대해 exponential backoff 재시도
 * - safeClick: 여러 셀렉터 후보를 순서대로 시도
 * - waitForPageReady: 네트워크 안정화 대기
 * - safeType: 입력 후 값 검증
 */
export abstract class BaseAutomation {
  protected browser: Browser | null = null;
  protected context: BrowserContext | null = null;
  protected page: Page | null = null;

  protected readonly headless: boolean;
  protected readonly slowMo: number;
  protected readonly userAgent: string;

  constructor(options: BrowserOptions = {}) {
    this.headless = options.headless ?? (process.env.BROWSER_HEADLESS !== 'false');
    this.slowMo   = options.slowMo ?? parseInt(process.env.BROWSER_SLOW_MO ?? '200');
    this.userAgent = options.userAgent ?? DEFAULT_USER_AGENT;
  }

  protected async launch(): Promise<Page> {
    this.browser = await chromium.launch({
      headless: this.headless,
      slowMo: this.slowMo,
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-blink-features=AutomationControlled',
        '--disable-dev-shm-usage',
        '--disable-gpu',
      ],
    });

    this.context = await this.browser.newContext({
      userAgent: this.userAgent,
      viewport: { width: 1280, height: 900 },
      locale: 'ko-KR',
      timezoneId: 'Asia/Seoul',
      extraHTTPHeaders: { 'Accept-Language': 'ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7' },
    });

    // navigator.webdriver 속성 제거
    await this.context.addInitScript(() => {
      Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
    });

    this.page = await this.context.newPage();
    logger.info(`[Browser] 시작 (headless: ${this.headless}, slowMo: ${this.slowMo}ms)`);
    return this.page;
  }

  protected async close(): Promise<void> {
    try {
      await this.browser?.close();
    } catch {
      logger.warn('[Browser] 종료 중 오류 (무시)');
    } finally {
      this.browser = null;
      this.context = null;
      this.page    = null;
    }
  }

  // ── 재시도 ────────────────────────────────────────────────────

  /**
   * exponential backoff 재시도 래퍼
   * @param name  로그에 표시할 작업명
   * @param fn    실행할 비동기 함수
   * @param maxRetries  최대 재시도 횟수 (기본 2)
   * @param baseDelayMs 초기 대기 시간 ms (기본 1500, 2배씩 증가)
   */
  protected async withRetry<T>(
    name: string,
    fn: () => Promise<T>,
    maxRetries = 2,
    baseDelayMs = 1500,
  ): Promise<T> {
    let lastError: unknown;
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return await fn();
      } catch (err) {
        lastError = err;
        if (attempt < maxRetries) {
          const delay = baseDelayMs * Math.pow(2, attempt);
          logger.warn(`[Retry] ${name} 실패 (${attempt + 1}/${maxRetries}), ${delay}ms 후 재시도: ${(err as Error).message}`);
          await this.sleep(delay);
        }
      }
    }
    throw lastError;
  }

  // ── 클릭 ─────────────────────────────────────────────────────

  /**
   * 여러 셀렉터 후보 중 첫 번째로 찾은 요소를 클릭
   * 실패해도 throw하지 않고 false 반환
   */
  protected async safeClick(selectors: string[], timeout = 8_000): Promise<boolean> {
    for (const sel of selectors) {
      try {
        await this.page!.waitForSelector(sel, { timeout, state: 'visible' });
        await this.randomDelay(200, 500);
        await this.page!.click(sel);
        return true;
      } catch {
        // 다음 후보 시도
      }
    }
    logger.warn(`[Browser] safeClick 실패 — 후보 없음: ${selectors.join(' | ')}`);
    return false;
  }

  /**
   * 단일 셀렉터 클릭 (실패 시 throw)
   */
  protected async waitAndClick(selector: string, timeout = 10_000): Promise<void> {
    await this.page!.waitForSelector(selector, { timeout, state: 'visible' });
    await this.randomDelay(200, 600);
    await this.page!.click(selector);
  }

  // ── 입력 ─────────────────────────────────────────────────────

  /**
   * 사람처럼 타이핑 (한 글자씩 입력)
   */
  protected async humanType(selector: string, text: string): Promise<void> {
    await this.page!.waitForSelector(selector, { state: 'visible', timeout: 8_000 });
    await this.page!.click(selector);
    await this.page!.fill(selector, '');
    await this.page!.type(selector, text, { delay: 80 + Math.random() * 80 });
  }

  /**
   * 입력 후 실제 값 검증 (오타 방지)
   */
  protected async safeType(selector: string, text: string, maxRetries = 2): Promise<void> {
    for (let i = 0; i <= maxRetries; i++) {
      await this.humanType(selector, text);
      const actual = await this.page!.$eval(
        selector,
        (el) => (el as HTMLInputElement).value,
      );
      if (actual === text) return;
      logger.warn(`[Browser] 입력값 불일치 재시도 (${i + 1}/${maxRetries}): 기대="${text}", 실제="${actual}"`);
      await this.sleep(500);
    }
    logger.warn(`[Browser] safeType 최종 실패: ${selector}`);
  }

  // ── 대기 ─────────────────────────────────────────────────────

  /**
   * 네트워크 안정화 대기 (timeout 내 idle 상태 진입 확인)
   */
  protected async waitForPageReady(timeout = 15_000): Promise<void> {
    try {
      await this.page!.waitForLoadState('networkidle', { timeout });
    } catch {
      logger.warn('[Browser] networkidle 타임아웃 — domcontentloaded로 폴백');
      await this.page!.waitForLoadState('domcontentloaded', { timeout: 5_000 });
    }
  }

  /**
   * 특정 텍스트가 화면에 나타날 때까지 대기
   */
  protected async waitForText(text: string, timeout = 10_000): Promise<boolean> {
    try {
      await this.page!.waitForFunction(
        (t) => document.body.innerText.includes(t),
        text,
        { timeout },
      );
      return true;
    } catch {
      return false;
    }
  }

  /**
   * 랜덤 딜레이 (봇 감지 방지)
   */
  protected async randomDelay(min = 500, max = 1500): Promise<void> {
    await this.sleep(Math.floor(Math.random() * (max - min + 1)) + min);
  }

  protected sleep(ms: number): Promise<void> {
    return new Promise((r) => setTimeout(r, ms));
  }

  // ── 스크린샷 ─────────────────────────────────────────────────

  protected async screenshot(name: string): Promise<string> {
    const path = `screenshots/${name}_${Date.now()}.png`;
    try {
      await this.page?.screenshot({ path, fullPage: true });
      logger.info(`[Browser] 스크린샷: ${path}`);
    } catch {
      logger.warn(`[Browser] 스크린샷 실패: ${path}`);
    }
    return path;
  }
}
