package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Opening balances against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class OpeningServiceIT {

    private static final AtomicInteger CODES = new AtomicInteger(7500);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private OpeningService opening;

    @Autowired
    private JournalService journal;

    @Autowired
    private ChartService chart;

    @Autowired
    private BooksSettingsService settings;

    private @org.jspecify.annotations.Nullable LocalDate originalStart;

    @BeforeEach
    void rememberStart() {
        originalStart = settings.get().startDate();
    }

    @AfterEach
    void restoreStart() {
        jdbc.sql("UPDATE books_setting SET start_date = :start WHERE id = 1")
                .param("start", originalStart)
                .update();
    }

    private Account newAccount(AccountType type) {
        var code = "%04d".formatted(CODES.incrementAndGet());
        return chart.create(new AccountForm(code, "Opening test " + code, type, null, null));
    }

    private void startBooks(LocalDate date) {
        jdbc.sql("UPDATE books_setting SET start_date = :start WHERE id = 1")
                .param("start", date)
                .update();
    }

    @Test
    void openingNeedsStartDate() {
        jdbc.sql("UPDATE books_setting SET start_date = NULL WHERE id = 1").update();

        assertThatThrownBy(() -> opening.save(List.of(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("Set the books start date first");
                });
    }

    @Test
    void openingSavedTwiceLeavesOneLiveEntry() {
        startBooks(LocalDate.of(2026, 4, 1));
        var stock = newAccount(AccountType.ASSET);
        var loan = newAccount(AccountType.LIABILITY);

        var first = opening.save(
                List.of(
                        new OpeningBalanceLine(stock.id(), new BigDecimal("500"), BigDecimal.ZERO),
                        new OpeningBalanceLine(loan.id(), BigDecimal.ZERO, new BigDecimal("200"))),
                "Asha Rao");
        var second = opening.save(
                List.of(
                        new OpeningBalanceLine(stock.id(), new BigDecimal("800"), BigDecimal.ZERO),
                        new OpeningBalanceLine(loan.id(), BigDecimal.ZERO, new BigDecimal("300"))),
                "Asha Rao");

        assertThat(first.entryId()).isNotNull();
        assertThat(second.entryId()).isNotNull().isNotEqualTo(first.entryId());
        assertThat(journal.get(first.entryId()).reversedById()).isNotNull();
        assertThat(journal.get(second.entryId()).reversedById()).isNull();

        var atStart = journal.trialBalance(LocalDate.of(2026, 4, 1));
        assertThat(atStart.lines())
                .filteredOn(line -> line.accountId() == stock.id())
                .singleElement()
                .satisfies(line -> assertThat(line.debitRupees()).isEqualByComparingTo("800"));
        var trial = journal.trialBalance(LocalDate.of(2099, 1, 1));
        assertThat(trial.balanced()).isTrue();
        assertThat(trial.lines())
                .anySatisfy(line -> {
                    assertThat(line.accountId()).isEqualTo(stock.id());
                    assertThat(line.debitRupees()).isEqualByComparingTo("800");
                })
                .anySatisfy(line -> {
                    assertThat(line.accountId()).isEqualTo(loan.id());
                    assertThat(line.creditRupees()).isEqualByComparingTo("300");
                });
        var saved = opening.get();
        assertThat(saved.entryId()).isEqualTo(second.entryId());
        assertThat(saved.lines()).anySatisfy(line -> {
            assertThat(line.accountId()).isEqualTo(stock.id());
            assertThat(line.debitRupees()).isEqualByComparingTo("800");
        });
    }
}
