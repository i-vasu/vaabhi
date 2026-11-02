package com.vaabhi.money;

import com.vaabhi.tailoring.PaymentMode;

/** Money in and out through one payment mode. */
public record ModeTotal(PaymentMode mode, long inPaise, long outPaise) {}
