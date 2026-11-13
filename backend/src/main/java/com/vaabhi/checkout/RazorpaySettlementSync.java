package com.vaabhi.checkout;

import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Keeps {@code razorpay_settlement} in step with Razorpay: every morning (and on demand) the settled batches are
 * fetched page by page and each processed one is stored once. It never posts to the books; the sweeper does that.
 */
@Component
class RazorpaySettlementSync {

    private static final Logger log = LoggerFactory.getLogger(RazorpaySettlementSync.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int PAGE = 100;

    private final JdbcClient jdbc;
    private final RazorpayClient razorpay;
    private final Clock clock;

    RazorpaySettlementSync(JdbcClient jdbc, RazorpayClient razorpay, Clock clock) {
        this.jdbc = jdbc;
        this.razorpay = razorpay;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Kolkata")
    void scheduled() {
        try {
            var outcome = syncNow();
            if (outcome.error() == null) {
                log.info("Razorpay settlement sync added {}", outcome.added());
            } else {
                log.warn("Razorpay settlement sync failed: {}", outcome.error());
            }
        } catch (RuntimeException e) {
            log.warn("Razorpay settlement sync failed", e);
        }
    }

    /**
     * Fetches settlements from the day before the latest stored one (or 90 days back when there are none) up to now
     * and stores the processed ones. Rows from pages fetched before an error stay stored.
     */
    public RazorpaySyncOutcome syncNow() {
        if (!razorpay.configured()) {
            return new RazorpaySyncOutcome(false, 0, null);
        }
        var latest = jdbc.sql("SELECT MAX(settled_on) FROM razorpay_settlement")
                .query(LocalDate.class)
                .list()
                .stream()
                .filter(day -> day != null)
                .findFirst()
                .orElse(null);
        var now = clock.instant();
        long from = latest == null
                ? now.minus(90, ChronoUnit.DAYS).getEpochSecond()
                : latest.minusDays(1).atStartOfDay(IST).toEpochSecond();
        long to = now.getEpochSecond();
        int added = 0;
        try {
            for (int skip = 0; ; skip += PAGE) {
                var page = razorpay.settlements(from, to, skip);
                for (var settlement : page) {
                    if ("processed".equals(settlement.status())) {
                        added += insert(settlement);
                    }
                }
                if (page.size() < PAGE) {
                    break;
                }
            }
            return new RazorpaySyncOutcome(true, added, null);
        } catch (RuntimeException e) {
            return new RazorpaySyncOutcome(true, added, message(e));
        }
    }

    private int insert(RazorpaySettlementData settlement) {
        var settledOn = Instant.ofEpochSecond(settlement.createdAt()).atZone(IST).toLocalDate();
        return jdbc.sql("""
                        INSERT INTO razorpay_settlement
                            (id, utr, settled_on, gross_rupees, fee_rupees, tax_rupees, net_rupees)
                        VALUES (:id, :utr, :settledOn, :gross, :fee, :tax, :net)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", settlement.id())
                .param("utr", settlement.utr())
                .param("settledOn", settledOn)
                .param("gross", rupees(settlement.amountPaise() + settlement.feesPaise()))
                .param("fee", rupees(settlement.feesPaise() - settlement.taxPaise()))
                .param("tax", rupees(settlement.taxPaise()))
                .param("net", rupees(settlement.amountPaise()))
                .update();
    }

    private static BigDecimal rupees(long paise) {
        return Rupees.of(BigDecimal.valueOf(paise).movePointLeft(2));
    }

    private static String message(RuntimeException e) {
        if (e instanceof ResponseStatusException r && r.getReason() != null) {
            return r.getReason();
        }
        var text = e.getMessage();
        return text == null || text.isBlank() ? e.getClass().getSimpleName() : text;
    }
}
