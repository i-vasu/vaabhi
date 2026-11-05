package com.vaabhi.shared.sms;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WhatsApp settings under {@code vaabhi.whatsapp}.
 *
 * @param provider {@code log} (development: prints messages to the backend log) or {@code msg91}
 * @param authKey MSG91 auth key (the same key as for SMS)
 * @param integratedNumber the WhatsApp Business number connected in MSG91, with country code and no plus, e.g.
 *     {@code 917569189366}
 * @param url MSG91's WhatsApp template endpoint
 * @param languageCode the language the templates were approved in, e.g. {@code en}
 * @param namespace the templates' namespace, when MSG91 shows one (left out when blank)
 * @param templates logical template name → approved WhatsApp template name, e.g. {@code web_order_placed:
 *     vaabhi_order_placed}; a message without an entry is not sent on WhatsApp
 * @param params logical template name → the variables filling {{1}}, {{2}}, … in order
 */
@ConfigurationProperties("vaabhi.whatsapp")
public record WhatsAppProperties(
        SmsProperties.Provider provider,
        @Nullable String authKey,
        @Nullable String integratedNumber,
        @Nullable String url,
        @Nullable String languageCode,
        @Nullable String namespace,
        @Nullable Map<String, String> templates,
        @Nullable Map<String, List<String>> params) {

    public WhatsAppProperties {
        provider = provider == null ? SmsProperties.Provider.LOG : provider;
        url = url == null || url.isBlank()
                ? "https://api.msg91.com/api/v5/whatsapp/whatsapp-outbound-message/bulk/"
                : url;
        languageCode = languageCode == null || languageCode.isBlank() ? "en" : languageCode;
        templates = templates == null ? Map.of() : Map.copyOf(templates);
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /** The approved template name for a logical template, or null when it is not set up. */
    @Nullable
    String templateName(String template) {
        var name = templates.get(template);
        return name == null || name.isBlank() ? null : name.trim();
    }
}
