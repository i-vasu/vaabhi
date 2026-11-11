package com.vaabhi.shared.whatsapp;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development only: writes each WhatsApp message that has a template set up to the log instead of sending it. */
class LogWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(LogWhatsAppSender.class);

    private final WhatsAppProperties properties;

    LogWhatsAppSender(WhatsAppProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean configured(String template) {
        return properties.templateName(template) != null;
    }

    @Override
    public void send(String phone, String template, Map<String, String> variables) {
        log.info(
                "WhatsApp (not sent, dev log provider) to {} [{} = {}]: {}",
                phone,
                template,
                properties.templateName(template),
                variables);
    }
}
