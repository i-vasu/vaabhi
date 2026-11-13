package com.vaabhi.accounts;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The synced Razorpay settlements and the entry posted for each (read by SQL; accounts never imports checkout). */
@Service
class RazorpaySettlementQuery {

    private final JdbcClient jdbc;

    RazorpaySettlementQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The 200 newest settlements, newest first. */
    @Transactional(readOnly = true)
    List<RazorpaySettlementView> list() {
        return jdbc.sql("""
                        SELECT s.id, s.utr, s.settled_on, s.gross_rupees, s.fee_rupees, s.tax_rupees, s.net_rupees,
                               j.id AS journal_entry_id, j.number AS journal_number
                        FROM razorpay_settlement s
                        LEFT JOIN journal_entry j
                               ON j.source_type = 'RAZORPAY_SETTLEMENT' AND j.source_step = 'SETTLE'
                              AND j.source_id = s.seq
                        ORDER BY s.settled_on DESC, s.seq DESC
                        LIMIT 200
                        """)
                .query(RazorpaySettlementView.class)
                .list();
    }
}
