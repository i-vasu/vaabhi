package com.vaabhi.accounts;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Input GST for one month: tax on supplier bills plus the GST inside Razorpay gateway fees. */
public record InputGstMonth(
        YearMonth month,
        BigDecimal taxableRupees,
        BigDecimal cgstRupees,
        BigDecimal sgstRupees,
        BigDecimal igstRupees,
        BigDecimal gatewayGstRupees,
        BigDecimal totalRupees) {}
