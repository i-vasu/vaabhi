package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The books status shown on the Accounts home page. */
@Service
public class AccountsStatusService {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    private final BooksSettingsService settings;
    private final PostingProblemService problems;
    private final JournalService journal;
    private final Clock clock;

    AccountsStatusService(
            BooksSettingsService settings, PostingProblemService problems, JournalService journal, Clock clock) {
        this.settings = settings;
        this.problems = problems;
        this.journal = journal;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountsStatus get() {
        var books = settings.get();
        var trial = journal.trialBalance(LocalDate.now(clock.withZone(SHOP_ZONE)));
        var difference = Rupees.of(
                trial.totalDebitRupees().subtract(trial.totalCreditRupees()).abs());
        return new AccountsStatus(books.startDate(), books.lastSweepAt(), problems.count(), difference);
    }
}
