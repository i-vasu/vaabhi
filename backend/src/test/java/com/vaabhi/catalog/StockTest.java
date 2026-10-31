package com.vaabhi.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StockTest {

    @ParameterizedTest
    @CsvSource({"0, ", "1, 1", "3, 3", "4, ", "250, "})
    void revealsOnlyLowStock(long quantity, Integer expected) {
        assertThat(Stock.stockLeft(quantity)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"1850000, 2200000, 16", "100, 100, 0", "100, 90, 0", "4999, 9999, 50"})
    void roundsDiscountLikeTheStorefront(int price, int mrp, int expected) {
        assertThat(Stock.discountPercent(price, mrp)).isEqualTo(expected);
    }

    @Test
    void parsesSortParameters() {
        assertThat(ProductSort.fromParam(" Price_ASC ")).isEqualTo(ProductSort.PRICE_ASC);
        assertThat(ProductSort.NEW.orderBy()).endsWith(", l.id DESC");
        assertThatThrownBy(() -> ProductSort.fromParam("cheapest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cheapest");
    }
}
