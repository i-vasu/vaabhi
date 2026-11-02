package com.vaabhi.money;

import java.util.List;

/**
 * Everything customers owe, oldest first.
 *
 * @param handedOverPaise owed on work already handed over (the money most at risk)
 */
public record Outstanding(long totalPaise, long handedOverPaise, List<Due> items) {}
