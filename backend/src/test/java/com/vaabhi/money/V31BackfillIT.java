package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaabhi.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V31 spreads each old invoice's tax over its lines. Runs the migrations up to V30 in a scratch database on the same
 * container (the migrations name the public schema, so a separate schema would not work), adds invoices, migrates to
 * V31 and checks the lines add up to their invoice.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class V31BackfillIT {

    private static final String SCRATCH = "v31_backfill";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PostgreSQLContainer postgres;

    private Flyway flyway(DriverManagerDataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static long webOrder(JdbcClient db, String number) {
        long customer = db.sql("INSERT INTO customer (name, phone) VALUES ('Test', :phone) RETURNING id")
                .param("phone", "+91987620003" + number.charAt(number.length() - 1))
                .query(Long.class)
                .single();
        return db.sql("""
                        INSERT INTO web_order (number, customer_id, status, payment_method, contact_name,
                            contact_phone, address_line1, city, state, pincode, items_rupees, shipping_rupees,
                            total_rupees)
                        VALUES (:number, :customer, 'PLACED', 'COD', 'Test', '+919876200000', '1 Street', 'Pune',
                            'Maharashtra', '411001', 1000, 0, 1050)
                        RETURNING id
                        """)
                .param("number", number)
                .param("customer", customer)
                .query(Long.class)
                .single();
    }

    private static void invoice(
            JdbcClient db, String number, String orderNumber, String cgst, String sgst, List<String> amounts) {
        long id = db.sql("""
                        INSERT INTO invoice (number, web_order_id, issued_on, order_total_rupees, gst_rate,
                            prices_include_tax, taxable_rupees, cgst_rupees, sgst_rupees, total_rupees, title,
                            buyer_name, seller_name)
                        VALUES (:number, :order, DATE '2026-05-01', 1050, 5, TRUE, 1000, :cgst, :sgst, 1050,
                            'Invoice', 'Test', 'vaabhi')
                        RETURNING id
                        """)
                .param("number", number)
                .param("order", webOrder(db, orderNumber))
                .param("cgst", new BigDecimal(cgst))
                .param("sgst", new BigDecimal(sgst))
                .query(Long.class)
                .single();
        int position = 0;
        for (var amount : amounts) {
            db.sql("""
                            INSERT INTO invoice_line (invoice_id, position, description, quantity, unit_rupees,
                                amount_rupees)
                            VALUES (:invoice, :position, 'Item', 1, :amount, :amount)
                            """)
                    .param("invoice", id)
                    .param("position", ++position)
                    .param("amount", new BigDecimal(amount))
                    .update();
        }
    }

    @Test
    void backfilledLinesSumToInvoice() {
        jdbc.sql("DROP DATABASE IF EXISTS " + SCRATCH + " WITH (FORCE)").update();
        jdbc.sql("CREATE DATABASE " + SCRATCH).update();
        try {
            var dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + SCRATCH),
                    postgres.getUsername(),
                    postgres.getPassword());
            flyway(dataSource, "30").migrate();
            var db = JdbcClient.create(dataSource);
            invoice(db, "OLD-1", "WEB-V31-1", "25.00", "25.01", List.of("333.33", "333.33", "383.35"));
            invoice(db, "OLD-2", "WEB-V31-2", "25.00", "25.00", List.of("1050.00"));

            flyway(dataSource, "31").migrate();

            var rows = db.sql("""
                            SELECT i.number, i.taxable_rupees, i.cgst_rupees, i.sgst_rupees, i.igst_rupees,
                                   i.supply_type, SUM(l.taxable_rupees) AS line_taxable,
                                   SUM(l.cgst_rupees) AS line_cgst, SUM(l.sgst_rupees) AS line_sgst,
                                   SUM(l.igst_rupees) AS line_igst, COUNT(l.id) AS lines
                            FROM invoice i JOIN invoice_line l ON l.invoice_id = i.id
                            GROUP BY i.id ORDER BY i.number
                            """)
                    .query((rs, n) -> new Object[] {
                        rs.getString("number"),
                        rs.getBigDecimal("taxable_rupees"),
                        rs.getBigDecimal("line_taxable"),
                        rs.getBigDecimal("cgst_rupees"),
                        rs.getBigDecimal("line_cgst"),
                        rs.getBigDecimal("sgst_rupees"),
                        rs.getBigDecimal("line_sgst"),
                        rs.getBigDecimal("igst_rupees"),
                        rs.getBigDecimal("line_igst"),
                        rs.getString("supply_type"),
                        rs.getLong("lines")
                    })
                    .list();

            assertThat(rows).hasSize(2);
            for (var row : rows) {
                assertThat((BigDecimal) row[2]).isEqualByComparingTo((BigDecimal) row[1]);
                assertThat((BigDecimal) row[4]).isEqualByComparingTo((BigDecimal) row[3]);
                assertThat((BigDecimal) row[6]).isEqualByComparingTo((BigDecimal) row[5]);
                assertThat((BigDecimal) row[7]).isEqualByComparingTo("0");
                assertThat((BigDecimal) row[8]).isEqualByComparingTo("0");
                assertThat(row[9]).isEqualTo("INTRA");
            }
            assertThat(rows.get(0)[10]).isEqualTo(3L);
            assertThat(rows.get(1)[10]).isEqualTo(1L);
        } finally {
            jdbc.sql("DROP DATABASE IF EXISTS " + SCRATCH + " WITH (FORCE)").update();
        }
    }
}
