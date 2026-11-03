package com.vaabhi.money;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything customers owe, oldest first.
 *
 * @param handedOverRupees owed on work already handed over (the money most at risk)
 */
public record Outstanding(BigDecimal totalRupees, BigDecimal handedOverRupees, List<Due> items) {}
