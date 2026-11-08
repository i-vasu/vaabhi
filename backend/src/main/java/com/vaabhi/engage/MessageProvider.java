package com.vaabhi.engage;

import com.vaabhi.settings.MessageTemplates;

/**
 * Sends WhatsApp / SMS through a paid provider. None is set up yet: without one every message becomes a link that
 * staff send from the shop's phone. A provider (e.g. the WhatsApp Business API or an SMS gateway) is added as a
 * bean implementing this interface.
 */
public interface MessageProvider {

    boolean configured();

    /** Sends the message and returns the provider's id for it; throws when sending fails. */
    String send(MessageTemplates.Channel channel, String toPhone, String body);
}
