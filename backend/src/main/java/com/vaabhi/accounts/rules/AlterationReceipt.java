package com.vaabhi.accounts.rules;

import org.springframework.stereotype.Component;

/** Alteration money received. */
@Component
class AlterationReceipt extends AlterationPaymentRule {

    AlterationReceipt(RuleSupport support) {
        super(support, false);
    }

    @Override
    public int order() {
        return 12;
    }
}
