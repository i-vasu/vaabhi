/**
 * Vendor-neutral SMS and WhatsApp sending (sign-in codes and order updates). Callers name a template and its
 * variables; the provider (MSG91 in production, the log in development) maps them to its API.
 */
@org.springframework.modulith.NamedInterface("sms")
package com.vaabhi.shared.sms;
