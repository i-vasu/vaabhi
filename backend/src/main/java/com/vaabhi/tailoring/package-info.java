/**
 * Tailoring: shop customers ("clients", so they never clash with online customer accounts) and their
 * measurements, the stitching price list, orders with items moving through production stages, payments, and
 * alterations. Publishes {@link com.vaabhi.tailoring.OrderEvent}s for notifications, invoices and the activity
 * log.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Tailoring")
package com.vaabhi.tailoring;
