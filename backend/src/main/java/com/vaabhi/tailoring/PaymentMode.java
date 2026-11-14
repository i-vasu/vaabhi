package com.vaabhi.tailoring;

/** How money changed hands. RAZORPAY and COD only come from web orders, never from counter forms. */
public enum PaymentMode {
    CASH,
    UPI,
    CARD,
    BANK,
    RAZORPAY,
    COD
}
