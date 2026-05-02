package com.harness.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Product 도메인 테스트")
class ProductTest {

    @Test
    @DisplayName("Product.create()로 RAW 상태 상품을 생성한다")
    void create_defaultStatusIsRaw() {
        Product product = Product.create(
                "P001", "구스다운 이불", 79000, 59000, "침구", "<p>설명</p>"
        );

        assertThat(product.getCafe24ProductId()).isEqualTo("P001");
        assertThat(product.getName()).isEqualTo("구스다운 이불");
        assertThat(product.getSalePrice()).isEqualTo(59000);
        assertThat(product.getStatus()).isEqualTo(Product.Status.RAW);
    }

    @Test
    @DisplayName("originalPrice가 null이면 0으로 기본값 설정")
    void create_nullOriginalPrice_defaultsToZero() {
        Product product = Product.create("P002", "이불", null, 30000, "침구", null);
        assertThat(product.getOriginalPrice()).isEqualTo(0);
    }

    @Test
    @DisplayName("상태 전이: RAW → READY → PUBLISHING → DONE")
    void statusTransition() {
        Product product = Product.create("P003", "이불", 50000, 40000, "침구", "설명");

        product.markReady();
        assertThat(product.getStatus()).isEqualTo(Product.Status.READY);

        product.markPublishing();
        assertThat(product.getStatus()).isEqualTo(Product.Status.PUBLISHING);

        product.markDone();
        assertThat(product.getStatus()).isEqualTo(Product.Status.DONE);
    }

    @Test
    @DisplayName("markError()는 ERROR 상태로 전이한다")
    void markError() {
        Product product = Product.create("P004", "이불", 50000, 40000, "침구", "설명");
        product.markError();
        assertThat(product.getStatus()).isEqualTo(Product.Status.ERROR);
    }

    @Test
    @DisplayName("이미지와 옵션을 추가할 수 있다")
    void addImageAndOption() {
        Product product = Product.create("P005", "이불", 50000, 40000, "침구", "설명");

        ProductImage img = ProductImage.of("https://img.com/1.jpg", 0, ProductImage.ImageType.REPRESENTATIVE);
        ProductOption opt = ProductOption.of("색상", "아이보리", 50, 0);

        product.addImage(img);
        product.addOption(opt);

        assertThat(product.getImages()).hasSize(1);
        assertThat(product.getOptions()).hasSize(1);
        assertThat(product.getImages().get(0).getImageUrl()).isEqualTo("https://img.com/1.jpg");
        assertThat(product.getOptions().get(0).getOptionValue()).isEqualTo("아이보리");
    }

    @Test
    @DisplayName("clearImages/clearOptions는 컬렉션을 비운다")
    void clearCollections() {
        Product product = Product.create("P006", "이불", 50000, 40000, "침구", "설명");
        product.addImage(ProductImage.of("https://img.com/1.jpg", 0, ProductImage.ImageType.DETAIL));
        product.addOption(ProductOption.of("색상", "화이트", 10, 0));

        product.clearImages();
        product.clearOptions();

        assertThat(product.getImages()).isEmpty();
        assertThat(product.getOptions()).isEmpty();
    }

    @Test
    @DisplayName("update()는 null이 아닌 필드만 갱신한다")
    void update_onlyNonNullFields() {
        Product product = Product.create("P007", "이불", 50000, 40000, "침구", "원래 설명");

        product.update("수정된 이불", null, 35000, null);

        assertThat(product.getName()).isEqualTo("수정된 이불");
        assertThat(product.getOriginalPrice()).isEqualTo(50000);  // null → 유지
        assertThat(product.getSalePrice()).isEqualTo(35000);
        assertThat(product.getDescription()).isEqualTo("원래 설명");  // null → 유지
    }
}
