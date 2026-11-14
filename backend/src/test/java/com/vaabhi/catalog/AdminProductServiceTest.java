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
        assertThat(AdminProductService.newSku("VB0142", "Rani pink", "Free size", java.util.Set.of()))
                .isEqualTo("VB0142-RAN-FREESIZE");
        assertThat(AdminProductService.newSku("VB0142", "Red", null, java.util.Set.of()))
                .isEqualTo("VB0142-RED");
        assertThat(AdminProductService.newSku("VB0142", "Redwood", "M", java.util.Set.of("VB0142-RED-M")))
                .isEqualTo("VB0142-RED-M-2");
        assertThat(AdminProductService.newSku("VB0142", "42", "M", java.util.Set.of()))
                .isEqualTo("VB0142-X-M");
    }
}
