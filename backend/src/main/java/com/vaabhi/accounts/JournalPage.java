package com.vaabhi.accounts;

import java.util.List;

public record JournalPage(List<JournalListItem> items, long total, int page, int size) {}
