/**
 * Vendor-neutral SMS sending (sign-in codes now; COD confirmation and order updates later). Callers name a
 * template and its variables; the provider (MSG91 in production, the log in development) maps them to its API.
 */
@org.springframework.modulith.NamedInterface("sms")
package com.vaabhi.shared.sms;
