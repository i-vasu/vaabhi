package com.vaabhi.checkout;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shipping: free from {@code freeShippingFromRupees} of items, otherwise {@code shippingRupees}. */
@ConfigurationProperties("vaabhi.checkout")
record CheckoutProperties(BigDecimal freeShippingFromRupees, BigDecimal shippingRupees) {

    CheckoutProperties {
        freeShippingFromRupees = freeShippingFromRupees == null ? BigDecimal.valueOf(1999) : freeShippingFromRupees;
        shippingRupees = shippingRupees == null ? BigDecimal.valueOf(99) : shippingRupees;
    }
}
