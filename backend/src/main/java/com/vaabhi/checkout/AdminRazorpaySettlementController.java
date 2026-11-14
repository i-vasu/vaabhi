package com.vaabhi.checkout;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Sync now" for Razorpay settlements; the path is OWNER-only in SecurityConfig. */
@RestController
class AdminRazorpaySettlementController {

    private final RazorpaySettlementSync sync;

    AdminRazorpaySettlementController(RazorpaySettlementSync sync) {
        this.sync = sync;
    }

    @PostMapping("/api/v1/admin/accounts/bank/razorpay/sync")
    RazorpaySyncOutcome sync() {
        return sync.syncNow();
    }
}
