package com.vaabhi.catalog;

import java.util.List;

public record AdminProductPage(List<AdminProductSummary> items, long total, int page, int pageSize) {}
