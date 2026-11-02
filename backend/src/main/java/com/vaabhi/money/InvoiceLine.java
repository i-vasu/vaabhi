package com.vaabhi.money;

/** One line of an invoice: an order item, or the delivery charge. */
public record InvoiceLine(String description, int quantity, long unitPaise, long amountPaise) {}
