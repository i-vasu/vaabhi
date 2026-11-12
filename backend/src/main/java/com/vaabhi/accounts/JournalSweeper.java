package com.vaabhi.accounts;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns existing records (payments, invoices, credit notes, refunds, expenses) dated on or after the books start date
 * into journal entries. Each source posts in its own transaction; one that fails is recorded as a posting problem and
 * retried on the next sweep. Does nothing until the start date is set.
 */
@Component
public class JournalSweeper {

    /** What one sweep did. */
    public record SweepResult(int posted, int problems) {}

    private static final Logger log = LoggerFactory.getLogger(JournalSweeper.class);
    private static final int BATCH = 500;
    private static final int MAX_MESSAGE = 500;

    private final List<PostingRule> rules;
    private final JournalService journal;
    private final BooksSettingsService settings;
    private final PostingProblemService problems;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    JournalSweeper(
            List<PostingRule> rules,
            JournalService journal,
            BooksSettingsService settings,
            PostingProblemService problems,
            PlatformTransactionManager transactions,
            Clock clock) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(PostingRule::order))
                .toList();
        this.journal = journal;
        this.settings = settings;
        this.problems = problems;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT2M")
    void scheduledSweep() {
        try {
            sweepNow();
        } catch (RuntimeException e) {
            log.warn("Journal sweep failed", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onBooksStarted(BooksStarted event) {
        try {
            sweepNow();
        } catch (RuntimeException e) {
            log.warn("Journal sweep after books start failed", e);
        }
    }

    public SweepResult sweepNow() {
        var start = settings.get().startDate();
        if (start == null) {
            return new SweepResult(0, 0);
        }
        if (!running.tryLock()) {
            return new SweepResult(0, 0);
        }
        int posted = 0;
        int failed = 0;
        try {
            for (var rule : rules) {
                for (long id : rule.pending(start, BATCH)) {
                    try {
                        tx.executeWithoutResult(status -> journal.post(rule.build(id), null));
                        problems.clear(rule.sourceType(), id, rule.step());
                        posted++;
                    } catch (RuntimeException e) {
                        problems.record(rule.sourceType(), id, rule.step(), message(e));
                        failed++;
                    }
                }
            }
        } finally {
            settings.markSwept(OffsetDateTime.now(clock));
            running.unlock();
        }
        return new SweepResult(posted, failed);
    }

    private static String message(RuntimeException e) {
        String text = e.getMessage();
        if (e instanceof ResponseStatusException r) {
            var reason = r.getReason();
            if (reason != null) {
                text = reason;
            }
        }
        if (text == null || text.isBlank()) {
            text = e.getClass().getSimpleName();
        }
        text = text.trim();
        return text.length() <= MAX_MESSAGE ? text : text.substring(0, MAX_MESSAGE);
    }
}
