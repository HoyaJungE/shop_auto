import { Page } from 'playwright';
import { BaseAutomation } from './base';
import { RegisterTask, TaskResult } from '../types';
import logger from '../logger';

/**
 * 오늘의집 파트너센터 상품 등록 자동화
 *
 * 흐름:
 *   1. partnerbucketplace.com 로그인
 *   2. 상품관리 > 신상품 등록 이동 (오로라 시스템)
 *   3. 카테고리 선택
 *   4. 기본 정보 / 이미지 / 옵션 입력
 *   5. 등록 요청 (심사 필요)
 *
 * ⚠️ 오늘의집은 API가 없어 Playwright가 유일한 자동화 수단.
 *    심사 과정이 있으므로 "등록 요청" 완료가 최종 성공 기준.
 */
export class OhouseAutomation extends BaseAutomation {

  async register(task: RegisterTask): Promise<TaskResult> {
    const { taskId, product, credentials } = task;

    try {
      const page = await this.launch();

      // ── 1. 로그인 ────────────────────────────────────────────
      await this.login(page, credentials.loginId, credentials.password);

      // ── 2. 상품 등록 페이지 이동 ─────────────────────────────
      logger.info('[Ohouse] 상품 등록 페이지 이동');
      await page.goto('https://www.partnerbucketplace.com/products/new', {
        waitUntil: 'networkidle',
      });

      // ── 3. 카테고리 선택 ─────────────────────────────────────
      await this.selectCategory(page, product.categoryCode);

      // ── 4. 기본 정보 입력 ────────────────────────────────────
      await this.fillBasicInfo(page, product);

      // ── 5. 이미지 입력 ───────────────────────────────────────
      await this.fillImages(page, product);

      // ── 6. 상세설명 ──────────────────────────────────────────
      await this.fillDescription(page, product.description);

      // ── 7. 옵션 입력 ─────────────────────────────────────────
      await this.fillOptions(page, product);

      // ── 8. 등록 요청 ─────────────────────────────────────────
      const platformProductId = await this.submitProduct(page, taskId);

      return {
        taskId,
        status: 'SUCCESS',
        platformProductId,
        completedAt: new Date().toISOString(),
      };
    } catch (e) {
      const screenshotPath = await this.screenshot(`ohouse_error_${taskId}`);
      return {
        taskId,
        status: 'FAILED',
        errorMessage: (e as Error).message,
        screenshotPath,
        completedAt: new Date().toISOString(),
      };
    } finally {
      await this.close();
    }
  }

  // ── Private ──────────────────────────────────────────────────

  private async login(page: Page, loginId: string, password: string): Promise<void> {
    logger.info('[Ohouse] 로그인 시도');
    await page.goto('https://www.partnerbucketplace.com/auth/login', {
      waitUntil: 'networkidle',
    });

    await this.humanType('input[type="email"], #email', loginId);
    await this.randomDelay(400, 900);
    await this.humanType('input[type="password"], #password', password);
    await this.randomDelay(400, 900);

    await this.waitAndClick('button[type="submit"]');
    await page.waitForURL('**/partnerbucketplace.com/**', { timeout: 20_000 });
    logger.info('[Ohouse] 로그인 성공');
  }

  private async selectCategory(page: Page, categoryCode: string): Promise<void> {
    logger.info(`[Ohouse] 카테고리 선택: ${categoryCode}`);

    // 카테고리 입력 필드
    await this.waitAndClick('.category-input, input[placeholder*="카테고리"]');
    await this.humanType('.category-input, input[placeholder*="카테고리"]', categoryCode);
    await this.randomDelay(600, 1200);

    // 드롭다운에서 선택
    await this.waitAndClick(
      `.category-dropdown li:first-child, [data-code="${categoryCode}"]`,
    );
    await this.randomDelay(500, 1000);
  }

  private async fillBasicInfo(page: Page, product: RegisterTask['product']): Promise<void> {
    logger.info('[Ohouse] 기본 정보 입력');

    await this.humanType('input[name="name"], #productName', product.name);
    await this.randomDelay(300, 700);

    // 정가
    const originalInput = await page.$('input[name="originalPrice"], #originalPrice');
    if (originalInput) {
      await originalInput.fill(String(product.originalPrice));
      await this.randomDelay(200, 500);
    }

    // 판매가
    await page.$eval(
      'input[name="salePrice"], #salePrice',
      (el, val) => { (el as HTMLInputElement).value = val; },
      String(product.salePrice),
    );
    await this.randomDelay(200, 500);
  }

  private async fillImages(page: Page, product: RegisterTask['product']): Promise<void> {
    logger.info('[Ohouse] 이미지 URL 입력');

    const representativeImg = product.images.find((img) => img.type === 'REPRESENTATIVE');
    if (!representativeImg) return;

    // URL 입력 방식 탭 선택
    const urlTab = await page.$('button:has-text("URL로 등록"), .image-url-tab');
    if (urlTab) {
      await urlTab.click();
      await this.randomDelay(300, 600);
    }

    const urlInputs = await page.$$('input[type="url"].image-url, .product-image-url');
    if (urlInputs.length > 0) {
      await urlInputs[0]!.fill(representativeImg.url);
      await this.randomDelay(300, 600);
    }

    // 추가 이미지
    const detailImgs = product.images.filter((img) => img.type === 'DETAIL');
    for (let i = 0; i < Math.min(detailImgs.length, urlInputs.length - 1); i++) {
      await urlInputs[i + 1]!.fill(detailImgs[i]!.url);
      await this.randomDelay(200, 400);
    }
  }

  private async fillDescription(page: Page, description: string): Promise<void> {
    logger.info('[Ohouse] 상세설명 입력');

    try {
      const htmlBtn = await page.$('button:has-text("HTML"), .editor-html-mode');
      if (htmlBtn) {
        await htmlBtn.click();
        await this.randomDelay(300, 600);
      }
      const editor = await page.$('textarea.detail-description, #productDetail');
      if (editor) await editor.fill(description);
    } catch {
      logger.warn('[Ohouse] 상세설명 입력 실패 (스킵)');
    }
  }

  private async fillOptions(page: Page, product: RegisterTask['product']): Promise<void> {
    if (product.options.length === 0) return;
    logger.info(`[Ohouse] 옵션 입력: ${product.options.length}개`);

    const addOptionBtn = await page.$('button:has-text("옵션 추가"), .add-option-btn');
    if (addOptionBtn) {
      await addOptionBtn.click();
      await this.randomDelay(400, 800);
    }

    const groups = [...new Set(product.options.map((o) => o.groupName))];
    for (let i = 0; i < groups.length; i++) {
      const groupInputs = await page.$$('input.option-name, .option-group-input');
      if (groupInputs[i]) {
        await groupInputs[i]!.fill(groups[i]!);
        await this.randomDelay(200, 500);
      }

      const values = product.options
        .filter((o) => o.groupName === groups[i])
        .map((o) => o.value)
        .join('\n');  // 오늘의집은 줄바꿈 구분인 경우가 있음

      const valueInputs = await page.$$('textarea.option-values, .option-value-textarea');
      if (valueInputs[i]) {
        await valueInputs[i]!.fill(values);
        await this.randomDelay(200, 500);
      }
    }
  }

  private async submitProduct(page: Page, taskId: string): Promise<string | undefined> {
    logger.info('[Ohouse] 상품 등록 요청');

    await this.waitAndClick('button:has-text("등록 요청"), .product-submit-btn');
    await this.randomDelay(1500, 3000);

    try {
      // 등록 완료 확인 모달 또는 리다이렉트
      const confirmBtn = await page.$('button:has-text("확인"), .modal-confirm');
      if (confirmBtn) {
        await confirmBtn.click();
        await this.randomDelay(500, 1000);
      }

      // 상품 ID 추출 시도
      const urlMatch = page.url().match(/\/products\/(\d+)/);
      const platformProductId = urlMatch?.[1];
      logger.info(`[Ohouse] 등록 요청 완료 — 상품 ID: ${platformProductId ?? '심사 중'}`);
      return platformProductId;
    } catch {
      await this.screenshot(`ohouse_success_${taskId}`);
      return undefined;
    }
  }
}
