package com.vaabhi.accounts;

import java.math.BigDecimal;

/** How one tax head's output was paid: by input credit, by cash, and the credit carried forward. */
public record Gstr3bSetOff(
        String head,
        BigDecimal outputRupees,
        BigDecimal itcUsedRupees,
        BigDecimal cashRupees,
        BigDecimal carriedForwardRupees) {}
