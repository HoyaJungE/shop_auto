import { Page } from 'playwright';
import { BaseAutomation } from './base';
import { RegisterTask, TaskResult } from '../types';
import logger from '../logger';

/**
 * 쿠팡 WING 상품 등록 자동화
 *
 * 흐름:
 *   1. wing.coupang.com 로그인
 *   2. 상품관리 > 상품등록 이동
 *   3. 카테고리 선택
 *   4. 상품명, 가격, 이미지, 옵션, 배송 정보 입력
 *   5. 임시저장 또는 등록 완료
 *
 * ⚠️ 셀렉터는 시행착오를 거쳐 조정 필요.
 *    실패 시 screenshotPath 경로 이미지를 확인할 것.
 */
export class CoupangAutomation extends BaseAutomation {

  async register(task: RegisterTask): Promise<TaskResult> {
    const { taskId, product, credentials } = task;

    try {
      const page = await this.launch();

      // ── 1. 로그인 ────────────────────────────────────────────
      await this.login(page, credentials.loginId, credentials.password);

      // ── 2. 상품 등록 페이지 이동 ─────────────────────────────
      logger.info('[Coupang] 상품 등록 페이지 이동');
      await page.goto('https://wing.coupang.com/vendor/product/create', {
        waitUntil: 'networkidle',
      });

      // ── 3. 카테고리 선택 ─────────────────────────────────────
      await this.selectCategory(page, product.categoryCode);

      // ── 4. 기본 정보 입력 ────────────────────────────────────
      await this.fillBasicInfo(page, product);

      // ── 5. 이미지 업로드 ─────────────────────────────────────
      await this.fillImages(page, product);

      // ── 6. 옵션 입력 ─────────────────────────────────────────
      await this.fillOptions(page, product);

      // ── 7. 배송 정보 ─────────────────────────────────────────
      await this.fillDelivery(page);

      // ── 8. 등록 완료 ─────────────────────────────────────────
      const platformProductId = await this.submitProduct(page, taskId);

      return {
        taskId,
        status: 'SUCCESS',
        platformProductId,
        completedAt: new Date().toISOString(),
      };
    } catch (e) {
      const screenshotPath = await this.screenshot(`coupang_error_${taskId}`);
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
    logger.info('[Coupang] 로그인 시도');
    await page.goto('https://wing.coupang.com/login', { waitUntil: 'networkidle' });

    await this.humanType('#loginId', loginId);
    await this.randomDelay(400, 800);
    await this.humanType('#password', password);
    await this.randomDelay(300, 700);

    await this.waitAndClick('button[type="submit"]');
    await page.waitForURL('**/wing.coupang.com/**', { timeout: 20_000 });
    logger.info('[Coupang] 로그인 성공');
  }

  private async selectCategory(page: Page, categoryCode: string): Promise<void> {
    logger.info(`[Coupang] 카테고리 선택: ${categoryCode}`);

    // 카테고리 검색창에 코드 또는 이름 입력
    await this.waitAndClick('.category-search-input, input[placeholder*="카테고리"]');
    await this.humanType('.category-search-input, input[placeholder*="카테고리"]', categoryCode);
    await this.randomDelay(500, 1000);

    // 검색 결과에서 해당 카테고리 클릭
    await this.waitAndClick(
      `.category-item[data-code="${categoryCode}"], .category-result-item:first-child`,
    );
    await this.randomDelay(500, 1000);
  }

  private async fillBasicInfo(page: Page, product: RegisterTask['product']): Promise<void> {
    logger.info('[Coupang] 기본 정보 입력');

    // 상품명
    await this.humanType('input[name="sellerProductName"], #productName', product.name);
    await this.randomDelay(300, 700);

    // 정가
    const originalPriceInput = await page.$('input[name="originalPrice"], #originalPrice');
    if (originalPriceInput) {
      await originalPriceInput.fill(String(product.originalPrice));
      await this.randomDelay(200, 500);
    }

    // 판매가
    await page.$eval(
      'input[name="salePrice"], #salePrice',
      (el, val) => { (el as HTMLInputElement).value = val; },
      String(product.salePrice),
    );
    await this.randomDelay(200, 500);

    // 상세설명 (HTML 에디터 or textarea)
    try {
      const descFrame = page.frame({ name: 'detail_editor' });
      if (descFrame) {
        await descFrame.$eval('body', (body, html) => { body.innerHTML = html; }, product.description);
      } else {
        await page.$eval(
          'textarea[name="description"], #description',
          (el, val) => { (el as HTMLTextAreaElement).value = val; },
          product.description,
        );
      }
    } catch {
      logger.warn('[Coupang] 상세설명 입력 실패 (스킵)');
    }
  }

  private async fillImages(page: Page, product: RegisterTask['product']): Promise<void> {
    logger.info('[Coupang] 이미지 URL 입력');

    const representativeImg = product.images.find((img) => img.type === 'REPRESENTATIVE');
    if (representativeImg) {
      // 이미지 URL 직접 입력 방식 (WING은 URL 입력 지원)
      const urlInputs = await page.$$('input[type="url"].image-url, .image-url-input');
      if (urlInputs.length > 0) {
        await urlInputs[0]!.fill(representativeImg.url);
      }
    }

    // 추가 이미지
    const detailImgs = product.images.filter((img) => img.type === 'DETAIL');
    const additionalInputs = await page.$$('input[type="url"].image-url, .image-url-input');
    for (let i = 0; i < Math.min(detailImgs.length, additionalInputs.length - 1); i++) {
      await additionalInputs[i + 1]!.fill(detailImgs[i]!.url);
      await this.randomDelay(200, 400);
    }
  }

  private async fillOptions(page: Page, product: RegisterTask['product']): Promise<void> {
    if (product.options.length === 0) return;
    logger.info(`[Coupang] 옵션 입력: ${product.options.length}개`);

    // 옵션 그룹 추출 (중복 제거)
    const groups = [...new Set(product.options.map((o) => o.groupName))];

    for (const group of groups) {
      // 옵션 그룹명 입력
      const groupInput = await page.$('.option-group-name input:last-child, .purchase-option-name');
      if (groupInput) {
        await groupInput.fill(group);
        await this.randomDelay(200, 500);
      }

      // 해당 그룹의 옵션 값들 입력
      const values = product.options
        .filter((o) => o.groupName === group)
        .map((o) => o.value);

      const valueInput = await page.$('.option-value-input input, .purchase-option-value');
      if (valueInput) {
        await valueInput.fill(values.join(','));  // 쉼표 구분 입력
        await this.randomDelay(200, 500);
      }
    }

    // 옵션 적용 버튼 클릭
    const applyBtn = await page.$('button.option-apply, button[data-action="applyOption"]');
    if (applyBtn) {
      await applyBtn.click();
      await this.randomDelay(500, 1000);
    }
  }

  private async fillDelivery(page: Page): Promise<void> {
    logger.info('[Coupang] 배송 정보 입력');

    // 무료배송 선택
    const freeDeliveryRadio = await page.$('input[value="FREE"], input[id*="free"]');
    if (freeDeliveryRadio) {
      await freeDeliveryRadio.click();
      await this.randomDelay(200, 500);
    }
  }

  private async submitProduct(page: Page, taskId: string): Promise<string | undefined> {
    logger.info('[Coupang] 상품 등록 제출');

    // 등록하기 버튼 클릭
    await this.waitAndClick('button[data-action="submit"], button.btn-register');
    await this.randomDelay(1000, 2000);

    // 성공 후 상품 ID 추출 시도
    try {
      await page.waitForURL('**/product/*/edit', { timeout: 15_000 });
      const urlMatch = page.url().match(/\/product\/(\d+)/);
      const platformProductId = urlMatch?.[1];
      logger.info(`[Coupang] 등록 완료 — 상품 ID: ${platformProductId}`);
      return platformProductId;
    } catch {
      // 등록 완료 모달에서 ID 추출 시도
      const idEl = await page.$('.product-id, [data-product-id]');
      if (idEl) {
        const id = await idEl.getAttribute('data-product-id') ?? await idEl.textContent();
        return id?.trim();
      }
      // 스크린샷 찍고 ID 없이 성공 처리
      await this.screenshot(`coupang_success_${taskId}`);
      return undefined;
    }
  }
}
