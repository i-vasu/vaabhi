package com.vaabhi.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaabhi.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The GSTR-1 month against real Postgres. Each test uses its own month, so they do not see each other's rows. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class Gstr1ServiceIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(7_000);
    private static final String GSTIN = "36ABCDE1234F1Z5";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Gstr1Service gstr1;

    private long webOrder() {
        long customer = jdbc.sql("INSERT INTO customer (name, phone) VALUES ('Test', :phone) RETURNING id")
                .param("phone", "+9198764" + "%05d".formatted(SEQUENCE.incrementAndGet()))
                .query(Long.class)
                .single();
        return jdbc.sql("""
                        INSERT INTO web_order (number, customer_id, status, payment_method, contact_name,
                            contact_phone, address_line1, city, state, pincode, items_rupees, shipping_rupees,
                            total_rupees)
                        VALUES (:number, :customer, 'PLACED', 'COD', 'Test', '+919876200000', '1 Street', 'Pune',
                            'Maharashtra', '411001', 1000, 0, 1000)
                        RETURNING id
                        """)
                .param("number", "WEB-G1-" + SEQUENCE.incrementAndGet())
                .param("customer", customer)
                .query(Long.class)
                .single();
    }

    private record Invoice(long id, String number) {}

    private Invoice invoice(
            YearMonth month, @Nullable String gstin, String supplyType, String place, String total, boolean cancelled) {
        var number = "T-INV-" + SEQUENCE.incrementAndGet();
        long id = jdbc.sql("""
                        INSERT INTO invoice (number, web_order_id, issued_on, order_total_rupees, gst_rate,
                            prices_include_tax, taxable_rupees, total_rupees, client_gstin, title, buyer_name,
                            seller_name, seller_state_code, place_of_supply, supply_type, cancelled_at)
                        VALUES (:number, :order, :issued, :total, 5, true, :total, :total, :gstin, 'Tax Invoice',
                            'Buyer', 'Vaabhi', '36', :place, :supply, CASE WHEN :cancelled THEN now() END)
                        RETURNING id
                        """)
                .param("number", number)
                .param("order", webOrder())
                .param("issued", month.atDay(10))
                .param("total", new BigDecimal(total))
                .param("gstin", gstin)
                .param("place", place)
                .param("supply", supplyType)
                .param("cancelled", cancelled)
                .query(Long.class)
                .single();
        return new Invoice(id, number);
    }

    /** A line of an invoice; the tax is taken as the given halves (or the integrated tax when inter-state). */
    private long line(
            Invoice invoice,
            @Nullable String hsn,
            int quantity,
            String rate,
            String taxable,
            String cgst,
            String igst) {
        return jdbc.sql("""
                        INSERT INTO invoice_line (invoice_id, position, description, quantity, unit_rupees,
                            amount_rupees, hsn_code, gst_rate, taxable_rupees, cgst_rupees, sgst_rupees, igst_rupees)
                        VALUES (:invoice, 1, 'Item', :quantity, :taxable, :taxable, :hsn, :rate, :taxable, :cgst,
                            :cgst, :igst)
                        RETURNING id
                        """)
                .param("invoice", invoice.id())
                .param("quantity", quantity)
                .param("hsn", hsn)
                .param("rate", new BigDecimal(rate))
                .param("taxable", new BigDecimal(taxable))
                .param("cgst", new BigDecimal(cgst))
                .param("igst", new BigDecimal(igst))
                .query(Long.class)
                .single();
    }

    private void creditNote(
            YearMonth month,
            Invoice invoice,
            long invoiceLineId,
            @Nullable String hsn,
            int quantity,
            String rate,
            String taxable,
            String cgst,
            String igst) {
        var tax = new BigDecimal(cgst).multiply(BigDecimal.TWO).add(new BigDecimal(igst));
        var total = new BigDecimal(taxable).add(tax);
        long id = jdbc.sql("""
                        INSERT INTO credit_note (number, invoice_id, reason, issued_on, title, buyer_name,
                            seller_name, seller_state_code, place_of_supply, supply_type, taxable_rupees,
                            cgst_rupees, sgst_rupees, igst_rupees, total_rupees)
                        SELECT :number, i.id, 'RETURN', :issued, 'Credit Note', 'Buyer', 'Vaabhi', '36',
                               i.place_of_supply, i.supply_type, :taxable, :cgst, :cgst, :igst, :total
                        FROM invoice i WHERE i.id = :invoice
                        RETURNING id
                        """)
                .param("number", "T-CN-" + SEQUENCE.incrementAndGet())
                .param("invoice", invoice.id())
                .param("issued", month.atDay(20))
                .param("taxable", new BigDecimal(taxable))
                .param("cgst", new BigDecimal(cgst))
                .param("igst", new BigDecimal(igst))
                .param("total", total)
                .query(Long.class)
                .single();
        jdbc.sql("""
                        INSERT INTO credit_note_line (credit_note_id, position, invoice_line_id, description,
                            hsn_code, quantity, unit_rupees, gst_rate, taxable_rupees, cgst_rupees, sgst_rupees,
                            igst_rupees, amount_rupees)
                        VALUES (:note, 1, :line, 'Item', :hsn, :quantity, :taxable, :rate, :taxable, :cgst, :cgst,
                            :igst, :taxable)
                        """)
                .param("note", id)
                .param("line", invoiceLineId)
                .param("hsn", hsn)
                .param("quantity", quantity)
                .param("rate", new BigDecimal(rate))
                .param("taxable", new BigDecimal(taxable))
                .param("cgst", new BigDecimal(cgst))
                .param("igst", new BigDecimal(igst))
                .update();
    }

    @Test
    void groupsIntoSections() {
        var month = YearMonth.of(2031, 1);
        var registered = invoice(month, GSTIN, "INTRA", "36", "1050.00", false);
        line(registered, "6204", 1, "5", "1000.00", "25.00", "0");
        var large = invoice(month, null, "INTER", "27", "150000.00", false);
        line(large, "6204", 1, "18", "127118.64", "0", "22881.36");
        var small1 = invoice(month, null, "INTRA", "36", "1050.00", false);
        line(small1, "6204", 1, "5", "1000.00", "25.00", "0");
        var small2 = invoice(month, null, "INTRA", "36", "2100.00", false);
        line(small2, "6204", 2, "5", "2000.00", "50.00", "0");

        var result = gstr1.month(month);

        assertThat(result.b2b()).singleElement().satisfies(row -> {
            assertThat(row.columns())
                    .containsEntry("GSTIN/UIN of Recipient", GSTIN)
                    .containsEntry("Invoice Number", registered.number())
                    .containsEntry("Place Of Supply", "36-Telangana")
                    .containsEntry("Invoice Type", "Regular B2B");
            assertThat(row.taxable()).isEqualByComparingTo("1000.00");
        });
        assertThat(result.b2cl()).singleElement().satisfies(row -> {
            assertThat(row.columns())
                    .containsEntry("Invoice Number", large.number())
                    .containsEntry("Place Of Supply", "27-Maharashtra");
            assertThat(row.igst()).isEqualByComparingTo("22881.36");
        });
        assertThat(result.b2cs()).singleElement().satisfies(row -> {
            assertThat(row.columns()).containsEntry("Type", "OE").containsEntry("Place Of Supply", "36-Telangana");
            assertThat(row.taxable()).isEqualByComparingTo("3000.00");
            assertThat(row.cgst()).isEqualByComparingTo("75.00");
        });
        assertThat(result.cdnr()).isEmpty();
        assertThat(result.cdnur()).isEmpty();
    }

    @Test
    void creditNoteNetsB2cs() {
        var month = YearMonth.of(2031, 2);
        var small = invoice(month, null, "INTRA", "36", "2100.00", false);
        long lineId = line(small, "6204", 2, "5", "2000.00", "50.00", "0");
        creditNote(month, small, lineId, "6204", 1, "5", "1000.00", "25.00", "0");

        var result = gstr1.month(month);

        assertThat(result.b2cs()).singleElement().satisfies(row -> {
            assertThat(row.taxable()).isEqualByComparingTo("1000.00");
            assertThat(row.cgst()).isEqualByComparingTo("25.00");
        });
        assertThat(result.cdnr()).isEmpty();
        assertThat(result.cdnur()).isEmpty();
    }

    @Test
    void hsnSummaryNetsCredits() {
        var month = YearMonth.of(2031, 3);
        var invoice = invoice(month, null, "INTRA", "36", "3150.00", false);
        long garments = line(invoice, "6204", 3, "5", "3000.00", "75.00", "0");
        line(invoice, "9965", 1, "18", "100.00", "9.00", "0");
        creditNote(month, invoice, garments, "6204", 1, "5", "1000.00", "25.00", "0");

        var result = gstr1.month(month);

        assertThat(result.hsn()).hasSize(2);
        var goods = result.hsn().stream()
                .filter(h -> h.hsn().equals("6204"))
                .findFirst()
                .orElseThrow();
        assertThat(goods.quantity()).isEqualTo(2);
        assertThat(goods.taxable()).isEqualByComparingTo("2000.00");
        assertThat(goods.cgst()).isEqualByComparingTo("50.00");
        assertThat(goods.value()).isEqualByComparingTo("2100.00");
        assertThat(goods.uqc()).isEqualTo("PCS-PIECES");
        assertThat(goods.description()).isEqualTo("Garments");
        var delivery = result.hsn().stream()
                .filter(h -> h.hsn().equals("9965"))
                .findFirst()
                .orElseThrow();
        assertThat(delivery.description()).isEqualTo("Delivery");
        assertThat(delivery.uqc()).isEqualTo("NA");
    }

    @Test
    void docsCountCancelled() {
        var month = YearMonth.of(2031, 4);
        var first = invoice(month, null, "INTRA", "36", "100.00", false);
        invoice(month, null, "INTRA", "36", "100.00", true);
        var last = invoice(month, null, "INTRA", "36", "100.00", false);

        var result = gstr1.month(month);

        assertThat(result.docs()).singleElement().satisfies(doc -> {
            assertThat(doc.nature()).isEqualTo("Invoices for outward supply");
            assertThat(doc.from()).isEqualTo(first.number());
            assertThat(doc.to()).isEqualTo(last.number());
            assertThat(doc.total()).isEqualTo(3);
            assertThat(doc.cancelled()).isEqualTo(1);
        });
    }

    @Test
    void csvHeaderOrder() {
        var month = YearMonth.of(2031, 5);
        var registered = invoice(month, GSTIN, "INTRA", "36", "1050.00", false);
        line(registered, "6204", 1, "5", "1000.00", "25.00", "0");

        var lines = gstr1.csv(month, "b2b").split("\n");

        assertThat(lines[0])
                .isEqualTo("GSTIN/UIN of Recipient,Receiver Name,Invoice Number,Invoice date,Invoice Value,"
                        + "Place Of Supply,Reverse Charge,Applicable % of Tax Rate,Invoice Type,E-Commerce GSTIN,"
                        + "Rate,Taxable Value,Cess Amount");
        assertThat(lines).hasSize(2);
        assertThat(lines[1])
                .startsWith(GSTIN + ",Buyer," + registered.number() + ",10-May-2031,1050.00,36-Telangana,N,");
        assertThatThrownBy(() -> gstr1.csv(month, "nope"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
