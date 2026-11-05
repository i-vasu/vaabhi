package com.vaabhi.tailoring;

import java.util.List;

/** A page of the customer list. */
public record ClientPage(List<ClientSummary> items, long total, int page, int pageSize) {}
