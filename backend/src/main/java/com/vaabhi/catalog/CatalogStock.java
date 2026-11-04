package com.vaabhi.catalog;

import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Takes sold items out of stock (an online order was placed) and puts them back (it was cancelled). */
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
}
