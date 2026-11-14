package com.vaabhi.accounts;

import java.time.YearMonth;
import java.util.List;

/** One month of GSTR-3B: outward supplies, inter-state supplies to unregistered buyers, input credit and its set-off. */
public record Gstr3b(
        YearMonth month,
        boolean gstOn,
        List<Gstr3bRow> outward,
        List<Gstr3bRow> interState,
        Gstr3bRow itc,
        List<Gstr3bSetOff> setOff) {}
