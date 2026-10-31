package com.vaabhi.catalog;

/** Lowest and highest price (paise) among products matching every filter except price. */
public record PriceRange(int minPaise, int maxPaise) {}
