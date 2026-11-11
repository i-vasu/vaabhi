package com.vaabhi.engage;

/**
 * Sends WhatsApp through a paid provider. None is set up yet: without one every message becomes a wa.me link that
 * staff send from the shop's phone. A provider (e.g. the WhatsApp Business API) is added as a bean implementing this
 * interface.
 */
public interface MessageProvider {

    boolean configured();

    /** Sends the message and returns the provider's id for it; throws when sending fails. */
    String send(String toPhone, String body);
}
