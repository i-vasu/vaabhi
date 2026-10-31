package com.vaabhi.catalog;

import java.util.List;

/** A filter section. {@code code} is the query parameter to send, e.g. {@code fabric=silk,cotton}. */
public record FilterGroup(String code, String name, FilterType type, List<FilterOption> options) {}
