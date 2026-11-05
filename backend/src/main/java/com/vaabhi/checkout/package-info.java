/**
 * Storefront checkout and online orders: the customer's bag becomes an order paid with Razorpay Checkout (or cash
 * on delivery), shipped by hand or through Shiprocket, then delivered or cancelled with a Razorpay refund. A paid
 * order is PLACED when the browser returns a signed payment, Razorpay's webhook reports it, or {@link
 * PaymentReconciler} finds it; stock is taken then.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Checkout")
package com.vaabhi.checkout;
