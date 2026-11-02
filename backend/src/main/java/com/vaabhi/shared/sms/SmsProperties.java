package com.vaabhi.shared.sms;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMS settings under {@code vaabhi.sms}.
 *
 * @param provider {@code log} (development: prints messages to the backend log) or {@code msg91}
 * @param msg91 MSG91 credentials and DLT-approved template ids
 */
@ConfigurationProperties("vaabhi.sms")
public record SmsProperties(Provider provider, Msg91 msg91) {

    public enum Provider {
        LOG,
        MSG91
    }

    public SmsProperties {
        provider = provider == null ? Provider.LOG : provider;
        msg91 = msg91 == null ? new Msg91(null, null, null) : msg91;
    }

    /**
     * @param authKey MSG91 auth key (dashboard → API)
     * @param url Flow API endpoint
     * @param templates logical template name → MSG91 template id, e.g. {@code otp: 65f0...}
     */
    public record Msg91(
            @Nullable String authKey,
            @Nullable String url,
            @Nullable Map<String, String> templates) {

        public Msg91 {
            url = url == null || url.isBlank() ? "https://control.msg91.com/api/v5/flow" : url;
            templates = templates == null ? Map.of() : Map.copyOf(templates);
        }
    }
}
