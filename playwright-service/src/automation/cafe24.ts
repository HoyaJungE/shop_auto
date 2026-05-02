import { Page } from 'playwright';
import { BaseAutomation } from './base';
import { CrawledProduct, CrawlRequest, CrawlResult, ProductImage, ProductOption } from '../types';
import logger from '../logger';

/**
 * Cafe24 관리자 크롤러
 *
 * 흐름:
 *   1. 관리자 로그인
 *   2. 상품 목록 페이지 이동
 *   3. 상품 목록 전체 수집 (페이지네이션)
 *   4. 각 상품 상세 페이지 진입 → 데이터 추출
 *   5. 로그아웃
 *
 * ⚠️ 셀렉터는 Cafe24 관리자 UI 버전마다 다를 수 있다.
 *    실제 실행 시 셀렉터가 깨지면 screenshot()으로 확인 후 수정할 것.
 */
export class Cafe24Crawler extends BaseAutomation {

  async crawl(req: CrawlRequest): Promise<CrawlResult> {
    const { taskId, credentials, limit } = req;
    const products: CrawledProduct[] = [];

    try {
      const page = await this.launch();

      // ── 1. 로그인 ────────────────────────────────────────────
      await this.login(page, credentials);

      // ── 2. 상품 목록 이동 ────────────────────────────────────
      logger.info('[Cafe24] 상품 목록 이동');
      await page.goto(`${credentials.shopUrl}/disp/admin/shop1/product/ProductListAdmin`, {
        waitUntil: 'networkidle',
      });

      // ── 3. 상품 목록 수집 (페이지네이션) ─────────────────────
      const productUrls = await this.collectProductUrls(page, credentials.shopUrl, limit);
      logger.info(`[Cafe24] 수집 대상 상품: ${productUrls.length}개`);

      // ── 4. 각 상품 상세 크롤링 ────────────────────────────────
      for (const url of productUrls) {
        try {
          const product = await this.crawlProductDetail(page, url, credentials.shopUrl);
          products.push(product);
          logger.info(`[Cafe24] 상품 수집 완료: ${product.name} (${product.cafe24ProductId})`);
          await this.randomDelay(800, 2000);  // 봇 감지 방지
        } catch (e) {
          logger.error(`[Cafe24] 상품 크롤링 실패: ${url} — ${(e as Error).message}`);
          await this.screenshot(`cafe24_error_${taskId}`);
          // 개별 상품 실패는 무시하고 계속 진행
        }
      }

      return { taskId, status: 'SUCCESS', products };
    } catch (e) {
      const screenshotPath = await this.screenshot(`cafe24_fatal_${taskId}`);
      return {
        taskId,
        status: 'FAILED',
        products,
        errorMessage: (e as Error).message,
      };
    } finally {
      await this.close();
    }
  }

  // ── Private ──────────────────────────────────────────────────

  private async login(
    page: Page,
    credentials: CrawlRequest['credentials'],
  ): Promise<void> {
    logger.info('[Cafe24] 로그인 시도');
    await page.goto(`${credentials.shopUrl}/disp/admin/shop1/login`, {
      waitUntil: 'networkidle',
    });

    await this.humanType('#member_id', credentials.loginId);
    await this.randomDelay(300, 700);
    await this.humanType('#member_passwd', credentials.password);
    await this.randomDelay(300, 700);

    await this.waitAndClick('#btnSubmit');
    await page.waitForURL('**/admin/**', { timeout: 15_000 });
    logger.info('[Cafe24] 로그인 성공');
  }

  private async collectProductUrls(
    page: Page,
    shopUrl: string,
    limit?: number,
  ): Promise<string[]> {
    const urls: string[] = [];
    let currentPage = 1;

    while (true) {
      // 페이지별 상품 링크 수집
      const pageUrls = await page.$$eval(
        'table.board_list tbody tr td a[href*="product_no"]',
        (anchors) =>
          anchors.map((a) => (a as HTMLAnchorElement).href).filter(Boolean),
      );

      urls.push(...pageUrls);
      logger.info(`[Cafe24] 목록 페이지 ${currentPage}: ${pageUrls.length}개 수집`);

      if (limit && urls.length >= limit) {
        return urls.slice(0, limit);
      }

      // 다음 페이지 존재 여부 확인
      const nextBtn = await page.$('a.next:not(.disabled)');
      if (!nextBtn) break;

      await nextBtn.click();
      await page.waitForLoadState('networkidle');
      await this.randomDelay(500, 1200);
      currentPage++;
    }

    return limit ? urls.slice(0, limit) : urls;
  }

  private async crawlProductDetail(
    page: Page,
    url: string,
    shopUrl: string,
  ): Promise<CrawledProduct> {
    await page.goto(url, { waitUntil: 'networkidle' });

    // 상품 번호 추출 (URL에서)
    const productNoMatch = url.match(/product_no=(\d+)/);
    const cafe24ProductId = productNoMatch?.[1] ?? String(Date.now());

    // ── 기본 정보 ─────────────────────────────────────────────
    const name = await page.$eval(
      '#product_name',
      (el) => (el as HTMLInputElement).value,
    ).catch(() => '');

    const originalPrice = await page.$eval(
      '#price',
      (el) => parseInt((el as HTMLInputElement).value.replace(/[^0-9]/g, '') || '0'),
    ).catch(() => 0);

    const salePrice = await page.$eval(
      '#sale_price',
      (el) => parseInt((el as HTMLInputElement).value.replace(/[^0-9]/g, '') || '0'),
    ).catch(() => originalPrice);

    const categoryName = await page.$eval(
      '.category_txt',
      (el) => el.textContent?.trim() ?? '',
    ).catch(() => '');

    // ── 상세설명 (HTML iframe) ────────────────────────────────
    const description = await this.extractDescription(page);

    // ── 이미지 ───────────────────────────────────────────────
    const images = await this.extractImages(page);

    // ── 옵션 ─────────────────────────────────────────────────
    const options = await this.extractOptions(page);

    return {
      cafe24ProductId,
      name,
      originalPrice,
      salePrice,
      categoryName,
      description,
      images,
      options,
    };
  }

  private async extractDescription(page: Page): Promise<string> {
    try {
      // Cafe24 상세설명은 SmartEditor iframe 내부에 있는 경우가 많다
      const frame = page.frame({ name: 'detail_editor_frame' });
      if (frame) {
        return await frame.$eval('body', (el) => el.innerHTML);
      }
      // fallback: textarea
      return await page.$eval(
        '#description',
        (el) => (el as HTMLTextAreaElement).value,
      );
    } catch {
      return '';
    }
  }

  private async extractImages(page: Page): Promise<ProductImage[]> {
    try {
      const images: ProductImage[] = [];

      // 대표 이미지
      const mainImgSrc = await page.$eval(
        '#image_preview img, .product_image img',
        (img) => (img as HTMLImageElement).src,
      ).catch(() => '');
      if (mainImgSrc) {
        images.push({ url: mainImgSrc, order: 0, type: 'REPRESENTATIVE' });
      }

      // 추가 이미지
      const addImgs = await page.$$eval(
        '.add_image_list img, .extra_image img',
        (imgs) => imgs.map((img) => (img as HTMLImageElement).src),
      ).catch(() => [] as string[]);

      addImgs.forEach((url, i) => {
        if (url) images.push({ url, order: i + 1, type: 'DETAIL' });
      });

      return images;
    } catch {
      return [];
    }
  }

  private async extractOptions(page: Page): Promise<ProductOption[]> {
    try {
      const options: ProductOption[] = [];

      // 옵션 테이블에서 옵션명/값 추출
      const rows = await page.$$('.option_table tr, table#opt_list tbody tr');
      for (const row of rows) {
        const cells = await row.$$eval('td', (tds) =>
          tds.map((td) => td.textContent?.trim() ?? ''),
        );
        if (cells.length >= 2) {
          options.push({
            groupName: cells[0] ?? '',
            value: cells[1] ?? '',
            additionalPrice: parseInt(cells[2]?.replace(/[^0-9-]/g, '') || '0'),
            stockQty: parseInt(cells[3]?.replace(/[^0-9]/g, '') || '0'),
          });
        }
      }

      return options;
    } catch {
      return [];
    }
  }
}
