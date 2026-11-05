package com.vaabhi.tailoring;

/**
 * Where an order stands, derived from its items' stages: BOOKED while every item is at the first stage,
 * IN_PROGRESS once work starts, READY when every item is ready (or delivered), DELIVERED when every item was
 * handed over. CANCELLED is set by hand.
 */
public enum OrderStatus {
    BOOKED,
    IN_PROGRESS,
    READY,
    DELIVERED,
    CANCELLED
}
