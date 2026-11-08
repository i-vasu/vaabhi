package com.vaabhi.tailoring;

import java.util.List;

/** A page of the alterations list. */
public record AlterationPage(List<AlterationSummary> items, long total, int page, int pageSize) {}
