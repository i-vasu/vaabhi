package com.vaabhi.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaabhi.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The sweeper's behaviour: idempotence, the start date, problems, and the daily check. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class JournalSweeperIT extends BooksFixtures {

    private boolean hasProblem(String type, long id, String step) {
        return problems.list().stream()
                .anyMatch(p -> p.sourceType().equals(type)
                        && p.sourceId() == id
                        && p.sourceStep().equals(step));
    }

    @Test
    void sweepTwicePostsOnce() {
        long expense = expense("MATERIAL", "800", "CASH");

        sweeper.sweepNow();
        sweeper.sweepNow();

        assertThat(entries("EXPENSE", expense)).hasSize(1);
    }

    @Test
    void nothingPostsWithoutStartDate() {
        long expense = expense("OTHER", "90", "CASH");
        jdbc.sql("UPDATE books_setting SET start_date = NULL WHERE id = 1").update();

        var result = sweeper.sweepNow();

        assertThat(result.posted()).isZero();
        assertThat(result.problems()).isZero();
        assertThat(entries("EXPENSE", expense)).isEmpty();
    }

    @Test
    void failingSourceBecomesProblemAndClears() {
        long expense = expense("UTILITIES", "450", "CASH");
        jdbc.sql("INSERT INTO period_lock (month, locked_by) VALUES (date_trunc('month', CURRENT_DATE), 'Test')")
                .update();
        try {
            var result = sweeper.sweepNow();

            assertThat(result.problems()).isPositive();
            assertThat(entries("EXPENSE", expense)).isEmpty();
            assertThat(problems.list())
                    .filteredOn(p -> p.sourceType().equals("EXPENSE") && p.sourceId() == expense)
                    .singleElement()
                    .satisfies(p -> assertThat(p.message()).contains("is locked"));
        } finally {
            jdbc.sql("DELETE FROM period_lock WHERE month = date_trunc('month', CURRENT_DATE)")
                    .update();
        }

        sweeper.sweepNow();

        assertThat(entries("EXPENSE", expense)).hasSize(1);
        assertThat(hasProblem("EXPENSE", expense, "EXPENSE")).isFalse();
    }

    @Test
    void lateReceiptFlaggedByDailyCheck() {
        long order = shopOrder("1050");
        shopPayment(order, "500", "CASH", base.minusMinutes(10));
        long invoice = shopInvoice(order, base.minusMinutes(5));
        sweeper.sweepNow();
        entry("INVOICE", invoice, "APPLY");

        shopPayment(order, "300", "CASH", base.minusMinutes(8));
        sweeper.sweepNow();
        check.run();

        try {
            assertThat(problems.list())
                    .filteredOn(p -> p.sourceType().equals("CHECK") && p.sourceId() == invoice)
                    .singleElement()
                    .satisfies(p -> {
                        assertThat(p.sourceStep()).isEqualTo("DEBTORS");
                        assertThat(p.message())
                                .startsWith("Debtors for INV/BT/")
                                .endsWith("is ₹550.00 but ₹250.00 is due");
                    });
        } finally {
            problems.clearType("CHECK");
        }
    }
}
