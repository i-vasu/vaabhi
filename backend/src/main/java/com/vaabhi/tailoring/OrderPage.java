package com.vaabhi.tailoring;

import java.util.List;

/** A page of the orders list. */
public record OrderPage(List<OrderSummary> items, long total, int page, int pageSize) {}
