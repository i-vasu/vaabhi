package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaabhi.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Invoice lines carry the invoice's tax, so the lines of every invoice add up to it. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class V31BackfillIT {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void backfilledLinesSumToInvoice() {
        var mismatched = jdbc.sql("""
                        SELECT i.id
                        FROM invoice i
                        JOIN invoice_line l ON l.invoice_id = i.id
                        GROUP BY i.id, i.taxable_rupees, i.cgst_rupees, i.sgst_rupees
                        HAVING SUM(l.taxable_rupees) <> i.taxable_rupees
                            OR SUM(l.cgst_rupees) <> i.cgst_rupees
                            OR SUM(l.sgst_rupees) <> i.sgst_rupees
                        """).query(Long.class).list();

        assertThat(mismatched).isEmpty();
    }
}
