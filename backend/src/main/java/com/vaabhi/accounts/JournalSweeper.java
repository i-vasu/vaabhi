package com.vaabhi.accounts;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
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
    private final boolean enabled;
    private final ReentrantLock running = new ReentrantLock();

    JournalSweeper(
            List<PostingRule> rules,
            JournalService journal,
            BooksSettingsService settings,
            PostingProblemService problems,
            PlatformTransactionManager transactions,
            Clock clock,
            @Value("${vaabhi.accounts.sweep.enabled:true}") boolean enabled) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(PostingRule::order))
                .toList();
        this.journal = journal;
        this.settings = settings;
        this.problems = problems;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT2M")
    void scheduledSweep() {
        if (!enabled) {
            return;
        }
        try {
            sweepNow();
        } catch (RuntimeException e) {
            log.warn("Journal sweep failed", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onBooksStarted(BooksStarted event) {
        if (!enabled) {
            return;
        }
        try {
            sweepNow();
        } catch (RuntimeException e) {
            log.warn("Journal sweep after books start failed", e);
        }
    }

    /** Sweeps now, waiting for any sweep already running. Posts nothing until the start date is set. */
    public SweepResult sweepNow() {
        var start = settings.get().startDate();
        if (start == null) {
            return new SweepResult(0, 0);
        }
        running.lock();
        try {
            int posted = 0;
            int failed = 0;
            try {
                for (var rule : rules) {
                    dropResolvedProblems(rule, start);
                    var failedIds = new HashSet<Long>();
                    while (true) {
                        // Ask for enough extra rows that already-failed ids cannot fill the whole batch.
                        var batch = rule.pending(start, BATCH + failedIds.size()).stream()
                                .filter(id -> !failedIds.contains(id))
                                .limit(BATCH)
                                .toList();
                        if (batch.isEmpty()) {
                            break;
                        }
                        for (long id : batch) {
                            try {
                                tx.executeWithoutResult(status -> {
                                    var entry = journal.post(rule.build(id), null);
                                    rule.posted(id, entry.id());
                                });
                                problems.clear(rule.sourceType(), id, rule.step());
                                posted++;
                            } catch (RuntimeException e) {
                                problems.record(rule.sourceType(), id, rule.step(), message(e));
                                failedIds.add(id);
                                failed++;
                            }
                        }
                    }
                }
            } finally {
                tx.executeWithoutResult(status -> settings.markSwept(OffsetDateTime.now(clock)));
            }
            return new SweepResult(posted, failed);
        } finally {
            running.unlock();
        }
    }

    /** Forgets problems for sources of this rule that are posted or no longer eligible. */
    private void dropResolvedProblems(PostingRule rule, LocalDate start) {
        var recorded = problems.idsFor(rule.sourceType(), rule.step());
        if (recorded.isEmpty()) {
            return;
        }
        var stillPending = new HashSet<>(rule.pending(start, Integer.MAX_VALUE));
        for (long id : recorded) {
            if (!stillPending.contains(id)) {
                problems.clear(rule.sourceType(), id, rule.step());
            }
        }
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
