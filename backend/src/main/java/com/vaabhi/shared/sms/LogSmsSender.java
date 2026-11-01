package com.vaabhi.shared.sms;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development only: writes each message (including codes) to the log instead of sending it. */
class LogSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public void send(String phone, String template, Map<String, String> variables) {
        log.info("SMS (not sent, dev log provider) to {} [{}]: {}", phone, template, variables);
    }
}
