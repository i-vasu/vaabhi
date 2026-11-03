package com.vaabhi.money;

import com.vaabhi.tailoring.PaymentMode;
import java.math.BigDecimal;

/** Money in and out through one payment mode. */
public record ModeTotal(PaymentMode mode, BigDecimal inRupees, BigDecimal outRupees) {}
