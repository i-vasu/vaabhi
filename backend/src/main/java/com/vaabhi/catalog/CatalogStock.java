package com.vaabhi.catalog;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Takes sold items out of stock (an online order was placed), puts them back (it was cancelled or returned), and
 * gives the parcel weights for shipping.
 */
@Service
public class CatalogStock {

    private final JdbcClient jdbc;

    CatalogStock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lowers stock by the quantity sold per SKU, never below zero: the customer has already paid, so a sale that
     * went over the last items is fulfilled by the shop rather than refused here.
     */
    @Transactional
    public void take(Map<String, Integer> quantityBySku) {
        quantityBySku.forEach((sku, quantity) ->
                jdbc.sql("""
                        UPDATE product_variant SET stock_qty = GREATEST(stock_qty - :quantity, 0)
                        WHERE sku = :sku
                        """).param("quantity", quantity).param("sku", sku).update());
    }

    /** Puts the quantities back, e.g. when an order is cancelled before it is delivered. */
    @Transactional
    public void restore(Map<String, Integer> quantityBySku) {
        quantityBySku.forEach((sku, quantity) -> jdbc.sql(
                        "UPDATE product_variant SET stock_qty = stock_qty + :quantity WHERE sku = :sku")
                .param("quantity", quantity)
                .param("sku", sku)
                .update());
    }

    /** The packed weight of one piece (grams) for the SKUs whose product has one set; others are left out. */
    @Transactional(readOnly = true)
    public Map<String, Integer> shippingWeightGrams(Collection<String> skus) {
        if (skus.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> weights = new HashMap<>();
        jdbc.sql("""
                        SELECT v.sku, p.shipping_weight_grams FROM product_variant v JOIN product p ON p.id = v.product_id
                        WHERE v.sku IN (:skus) AND p.shipping_weight_grams IS NOT NULL
                        """)
                .param("skus", skus)
                .query((rs, n) -> Map.entry(rs.getString("sku"), rs.getInt("shipping_weight_grams")))
                .list()
                .forEach(e -> weights.put(e.getKey(), e.getValue()));
        return weights;
    }
}
