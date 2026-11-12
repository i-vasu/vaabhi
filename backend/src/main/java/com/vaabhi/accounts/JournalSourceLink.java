package com.vaabhi.accounts;

/** The document an entry came from: its kind (the entry's source type), its id and a readable label. */
public record JournalSourceLink(String type, long id, String label) {}
