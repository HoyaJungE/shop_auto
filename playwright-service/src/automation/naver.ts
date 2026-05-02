import { Page } from 'playwright';
import { BaseAutomation } from './base';
import { RegisterTask, TaskResult } from '../types';
import logger from '../logger';

/**
 * 네이버 스마트스토어 상품 등록 자동화
 *
 * 흐름:
 *   1. sell.smartstore.naver.com 로그인 (네이버 계정)
 *   2. 상품관리 > 상품등록 이동
 *   3. 카테고리 선택
 *   4. 기본 정보 / 가격 / 옵션 / 배송 입력
 *   5. 저장 및 판매 신청
 */
export class NaverAutomation extends BaseAutomation {

  async register(task: RegisterTask): Promise<TaskResult> {
    const { taskId, product, credentials } = task;

    try {
      const page = await this.launch();

      // ── 1. 로그인 ────────────────────────────────────────────
      await this.login(page, credentials.loginId, credentials.password);

      // ── 2. 상품 등록 페이지 이동 ─────────────────────────────
      logger.info('[Naver] 상품 등록 페이지 이동');
      await page.goto('https://sell.smartstore.naver.com/o/sale/product/create', {
        waitUntil: 'networkidle',
      });

      // ── 3. 카테고리 선택 ─────────────────────────────────────
      await this.selectCategory(page, product.categoryCode);

      // ── 4. 기본 정보 입력 ────────────────────────────────────
      await this.fillBasicInfo(page, product);

      // ── 5. 이미지 업로드 ─────────────────────────────────────
      await this.fillImages(page, product);

      // ── 6. 상세설명 ──────────────────────────────────────────
      await this.fillDescription(page, product.description);

      // ── 7. 옵션 입력 ─────────────────────────────────────────
      await this.fillOptions(page, product);

      // ── 8. 배송 정보 ─────────────────────────────────────────
      await this.fillDelivery(page);

      // ── 9. 저장 ──────────────────────────────────────────────
      const platformProductId = await this.submitProduct(page, taskId);

      return {
        taskId,
        status: 'SUCCESS',
        platformProductId,
        completedAt: new Date().toISOString(),
      };
    } catch (e) {
      const screenshotPath = await this.screenshot(`naver_error_${taskId}`);
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
    logger.info('[Naver] 로그인 시도');
    await page.goto('https://nid.naver.com/nidlogin.login', { waitUntil: 'networkidle' });

    // 네이버 로그인 (일반 입력)
    await this.humanType('#id', loginId);
    await this.randomDelay(400, 900);
    await this.humanType('#pw', password);
    await this.randomDelay(400, 900);

    await this.waitAndClick('.btn_login');

    // 2차 인증 페이지가 나타날 수 있음 — 사용자가 직접 처리할 시간 확보
    try {
      await page.waitForURL('**/smartstore.naver.com/**', { timeout: 30_000 });
    } catch {
      // 2차 인증 등으로 대기 필요 시 60초 추가 대기
      logger.warn('[Naver] 로그인 대기 중 (2차 인증 필요할 수 있음) — 60초 대기');
      await page.waitForURL('**/smartstore.naver.com/**', { timeout: 60_000 });
    }
    logger.info('[Naver] 로그인 성공');
  }

  private async selectCategory(page: Page, categoryCode: string): Promise<void> {
    logger.info(`[Naver] 카테고리 선택: ${categoryCode}`);

    // 카테고리 설정 버튼 클릭
    await this.waitAndClick('[data-nclk="pc.category"], .category-set-btn');
    await this.randomDelay(500, 1000);

    // 카테고리 검색
    const searchInput = await page.$('.category-search-input input, #categorySearch');
    if (searchInput) {
      await searchInput.fill(categoryCode);
      await this.randomDelay(500, 1000);
    }

    // 결과 선택
    await this.waitAndClick(
      `.category-search-result li:first-child, [data-category-id="${categoryCode}"]`,
    );
    await this.randomDelay(500, 1000);

    // 적용 버튼
    const applyBtn = await page.$('.category-apply-btn, button:has-text("적용")');
    if (applyBtn) {
      await applyBtn.click();
      await this.randomDelay(500, 1000);
    }
  }

  private async fillBasicInfo(page: Page, product: RegisterTask['product']): Promise<void> {
    logger.info('[Naver] 기본 정보 입력');

    // 상품명
    await this.humanType('input[name="name"], #productName', product.name);
    await this.randomDelay(300, 600);

    // 판매가
    await page.$eval(
      'input[name="salePrice"], #salePrice',
      (el, val) => { (el as HTMLInputElement).value = val; },
      String(product.salePrice),
    );
    await this.randomDelay(200, 500);
  }

  private async fillImages(page: Page, product: RegisterTask['product']): Promise<void> {
    logger.info('[Naver] 이미지 입력');

    const representativeImg = product.images.find((img) => img.type === 'REPRESENTATIVE');
    if (!representativeImg) return;

    // URL 입력 탭이 있으면 선택
    const urlTabBtn = await page.$('button:has-text("URL"), .image-url-tab');
    if (urlTabBtn) {
      await urlTabBtn.click();
      await this.randomDelay(300, 600);
    }

    const urlInput = await page.$('.representative-image-url, input[placeholder*="이미지 URL"]');
    if (urlInput) {
      await urlInput.fill(representativeImg.url);
      await this.randomDelay(300, 600);

      const confirmBtn = await page.$('button:has-text("등록"), .url-image-confirm');
      if (confirmBtn) {
        await confirmBtn.click();
        await this.randomDelay(500, 1000);
      }
    }
  }

  private async fillDescription(page: Page, description: string): Promise<void> {
    logger.info('[Naver] 상세설명 입력');

    try {
      // 스마트에디터 or 직접 HTML 입력
      const htmlTab = await page.$('button:has-text("HTML"), .editor-html-btn');
      if (htmlTab) {
        await htmlTab.click();
        await this.randomDelay(300, 600);
      }

      const editor = await page.$('textarea.se-html-editor, #detail-description');
      if (editor) {
        await editor.fill(description);
      }
    } catch {
      logger.warn('[Naver] 상세설명 입력 실패 (스킵)');
    }
  }

  private async fillOptions(page: Page, product: RegisterTask['product']): Promise<void> {
    if (product.options.length === 0) return;
    logger.info(`[Naver] 옵션 입력: ${product.options.length}개`);

    // 옵션 사용 체크박스 활성화
    const useOption = await page.$('input[name="useOption"], #useOptionYn');
    if (useOption) {
      const checked = await useOption.isChecked();
      if (!checked) await useOption.click();
      await this.randomDelay(300, 600);
    }

    const groups = [...new Set(product.options.map((o) => o.groupName))];

    for (let i = 0; i < groups.length; i++) {
      const group = groups[i]!;
      const groupInputs = await page.$$('.option-group-name input, .option-name-input');
      if (groupInputs[i]) {
        await groupInputs[i]!.fill(group);
        await this.randomDelay(200, 500);
      }

      const values = product.options
        .filter((o) => o.groupName === group)
        .map((o) => o.value)
        .join(',');

      const valueInputs = await page.$$('.option-value-input input, .option-value');
      if (valueInputs[i]) {
        await valueInputs[i]!.fill(values);
        await this.randomDelay(200, 500);
      }
    }

    // 옵션 조합 생성
    const generateBtn = await page.$('button:has-text("옵션 목록 적용"), .option-generate-btn');
    if (generateBtn) {
      await generateBtn.click();
      await this.randomDelay(500, 1200);
    }
  }

  private async fillDelivery(page: Page): Promise<void> {
    logger.info('[Naver] 배송 정보 입력');

    // 무료배송 선택
    const freeRadio = await page.$('input[value="FREE_DELIVERY"], label:has-text("무료")');
    if (freeRadio) {
      await freeRadio.click();
      await this.randomDelay(200, 500);
    }
  }

  private async submitProduct(page: Page, taskId: string): Promise<string | undefined> {
    logger.info('[Naver] 상품 등록 제출');

    await this.waitAndClick('button[data-nclk="pc.save"], button:has-text("저장"), .save-btn');
    await this.randomDelay(1500, 3000);

    try {
      // URL에서 상품 ID 추출
      const urlMatch = page.url().match(/\/product\/(\d+)/);
      const platformProductId = urlMatch?.[1];
      logger.info(`[Naver] 등록 완료 — 상품 ID: ${platformProductId}`);
      return platformProductId;
    } catch {
      await this.screenshot(`naver_success_${taskId}`);
      return undefined;
    }
  }
}
