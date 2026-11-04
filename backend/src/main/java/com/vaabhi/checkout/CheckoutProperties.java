package com.vaabhi.checkout;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shipping: free from {@code freeShippingFromRupees} of items, otherwise {@code shippingRupees}. Returns: the
 * customer can ask to return items up to {@code returnDays} after delivery.
 */
@ConfigurationProperties("vaabhi.checkout")
record CheckoutProperties(BigDecimal freeShippingFromRupees, BigDecimal shippingRupees, Integer returnDays) {

    CheckoutProperties {
        freeShippingFromRupees = freeShippingFromRupees == null ? BigDecimal.valueOf(1999) : freeShippingFromRupees;
        shippingRupees = shippingRupees == null ? BigDecimal.valueOf(99) : shippingRupees;
        returnDays = returnDays == null ? 7 : returnDays;
    }
}
