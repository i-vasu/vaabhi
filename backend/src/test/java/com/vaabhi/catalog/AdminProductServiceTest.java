package com.vaabhi.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdminProductServiceTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Kanjeevaram Silk Saree – Rani Pink | kanjeevaram-silk-saree-rani-pink",
                "  Chikankari   Kurta Set (White) | chikankari-kurta-set-white",
                "Café Organza | cafe-organza",
                "!!! | product"
            })
    void slugifiesNames(String name, String slug) {
        assertThat(AdminProductService.slugify(name)).isEqualTo(slug);
    }

    @Test
    void capsLongSlugs() {
        assertThat(AdminProductService.slugify("a ".repeat(200)))
                .hasSizeLessThanOrEqualTo(150)
                .doesNotEndWith("-");
    }

    @Test
    void generatesReadableSkus() {
        assertThat(AdminProductService.newSku("Free size")).matches("VB-[A-Z2-9]{6}-FREESIZE");
        assertThat(AdminProductService.newSku(null)).matches("VB-[A-Z2-9]{6}");
        assertThat(AdminProductService.newSku("M")).isNotEqualTo(AdminProductService.newSku("M"));
    }
}
