import { Browser, BrowserContext, Page, chromium } from 'playwright';
import logger from '../logger';

export interface BrowserOptions {
  headless?: boolean;
  slowMo?: number;        // ms — 동작 간 지연 (봇 감지 방지)
  userAgent?: string;
}

const DEFAULT_USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';

/**
 * 브라우저 자동화 공통 기반 클래스
 *
 * 상속받아서 각 플랫폼(Cafe24, 쿠팡, 네이버, 오늘의집) 자동화 클래스를 구현한다.
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
    this.slowMo = options.slowMo ?? parseInt(process.env.BROWSER_SLOW_MO ?? '200');
    this.userAgent = options.userAgent ?? DEFAULT_USER_AGENT;
  }

  protected async launch(): Promise<Page> {
    this.browser = await chromium.launch({
      headless: this.headless,
      slowMo: this.slowMo,
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-blink-features=AutomationControlled',  // 자동화 감지 우회
        '--disable-dev-shm-usage',
        '--disable-gpu',
      ],
    });

    this.context = await this.browser.newContext({
      userAgent: this.userAgent,
      viewport: { width: 1280, height: 900 },
      locale: 'ko-KR',
      timezoneId: 'Asia/Seoul',
      // WebDriver 플래그 제거
      extraHTTPHeaders: {
        'Accept-Language': 'ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7',
      },
    });

    // navigator.webdriver 속성 제거 (봇 감지 우회)
    await this.context.addInitScript(() => {
      Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
    });

    this.page = await this.context.newPage();
    logger.info(`[Browser] 브라우저 시작 (headless: ${this.headless}, slowMo: ${this.slowMo}ms)`);
    return this.page;
  }

  protected async close(): Promise<void> {
    try {
      await this.browser?.close();
    } catch (e) {
      logger.warn('[Browser] 브라우저 종료 중 오류 (무시)');
    } finally {
      this.browser = null;
      this.context = null;
      this.page = null;
    }
  }

  /**
   * 스크린샷 저장 (실패 시 디버깅용)
   */
  protected async screenshot(name: string): Promise<string> {
    const path = `screenshots/${name}_${Date.now()}.png`;
    try {
      await this.page?.screenshot({ path, fullPage: true });
      logger.info(`[Browser] 스크린샷 저장: ${path}`);
    } catch {
      logger.warn(`[Browser] 스크린샷 실패: ${path}`);
    }
    return path;
  }

  /**
   * 랜덤 딜레이 (봇 감지 방지)
   * min~max ms 사이 랜덤 대기
   */
  protected async randomDelay(min = 500, max = 1500): Promise<void> {
    const delay = Math.floor(Math.random() * (max - min + 1)) + min;
    await new Promise((r) => setTimeout(r, delay));
  }

  /**
   * 사람처럼 타이핑 (한 글자씩 입력, 딜레이 포함)
   */
  protected async humanType(selector: string, text: string): Promise<void> {
    await this.page!.click(selector);
    await this.page!.fill(selector, '');  // 기존 내용 초기화
    await this.page!.type(selector, text, { delay: 80 + Math.random() * 80 });
  }

  /**
   * 요소가 나타날 때까지 대기 후 클릭
   */
  protected async waitAndClick(selector: string, timeout = 10_000): Promise<void> {
    await this.page!.waitForSelector(selector, { timeout });
    await this.randomDelay(200, 600);
    await this.page!.click(selector);
  }
}
