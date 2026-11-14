package com.vaabhi.money;

import java.math.BigDecimal;

/** The code and rate that apply to one item, after any price slab. */
public record TaxRate(String hsnCode, BigDecimal ratePercent, TaxClass.Kind kind) {}
