package com.vaabhi.catalog;

import java.math.BigDecimal;

/** Lowest and highest price (rupees) among products matching every filter except price. */
public record PriceRange(BigDecimal minRupees, BigDecimal maxRupees) {}
