package com.vaabhi.accounts.rules;

import org.springframework.stereotype.Component;

/** Alteration money given back. */
@Component
class AlterationRefund extends AlterationPaymentRule {

    AlterationRefund(RuleSupport support) {
        super(support, true);
    }

    @Override
    public int order() {
        return 63;
    }
}
