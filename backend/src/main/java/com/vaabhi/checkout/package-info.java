/**
 * Storefront checkout: the customer's bag becomes an online order, paid with Razorpay Checkout. The order is
 * created PENDING_PAYMENT with a matching Razorpay order; it turns PAID when the browser returns a signed payment
 * or Razorpay's webhook reports it (whichever comes first), and stock is taken then.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Checkout")
package com.vaabhi.checkout;
