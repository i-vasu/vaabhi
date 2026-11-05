package com.vaabhi.catalog;

import java.util.List;

/** A page of New arrivals: published products from every category, most recently published first. */
public record NewArrivalsPage(List<ProductSummary> items, long total, int page, int pageSize) {}
