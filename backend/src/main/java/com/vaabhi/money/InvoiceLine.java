package com.vaabhi.money;

import java.math.BigDecimal;

/** One line of an invoice: an order item, or the delivery charge. */
public record InvoiceLine(String description, int quantity, BigDecimal unitRupees, BigDecimal amountRupees) {}
