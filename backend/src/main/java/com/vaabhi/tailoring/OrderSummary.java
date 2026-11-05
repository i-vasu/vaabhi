package com.vaabhi.tailoring;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One row of the orders list: a counter order or a website order.
 *
 * @param clientId the shop customer; null for website orders (their customer is a storefront account)
 * @param status for website orders the nearest shop status: BOOKED while waiting to ship, READY once shipped
 * @param deliveryDate the promised date; null for website orders
 * @param overdue not yet delivered and the delivery date has passed
 * @param source SHOP (booked at the counter) or ONLINE (placed on the website)
 * @param onlineStatus the website order's own status (PLACED, SHIPPED, DELIVERED, CANCELLED); null for shop orders
 * @param openReturn a website order with a return still being handled
 */
public record OrderSummary(
        long id,
        String number,
        @Nullable Long clientId,
        String clientName,
        String clientPhone,
        OrderStatus status,
        LocalDate orderDate,
        @Nullable LocalDate deliveryDate,
        @Nullable LocalDate trialDate,
        int itemCount,
        String itemsLabel,
        BigDecimal totalRupees,
        BigDecimal balanceRupees,
        boolean overdue,
        OrderService.Source source,
        @Nullable String onlineStatus,
        boolean openReturn) {}
