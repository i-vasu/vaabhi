/**
 * Vendor-neutral WhatsApp sending (sign-in codes and order updates). Callers name a template and its variables; the
 * provider (MSG91 in production, the log in development) maps them to its approved WhatsApp template.
 */
@org.springframework.modulith.NamedInterface("whatsapp")
package com.vaabhi.shared.whatsapp;
