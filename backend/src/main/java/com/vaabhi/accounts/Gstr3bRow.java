package com.vaabhi.accounts;

import java.math.BigDecimal;

/** One line of GSTR-3B: the section it belongs to and the rupees in each tax column. */
public record Gstr3bRow(
        String section,
        String description,
        BigDecimal taxableRupees,
        BigDecimal igstRupees,
        BigDecimal cgstRupees,
        BigDecimal sgstRupees) {}
