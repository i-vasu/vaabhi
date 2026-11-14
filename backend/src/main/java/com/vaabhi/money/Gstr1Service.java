package com.vaabhi.money;

import com.vaabhi.settings.IndianStates;
import com.vaabhi.settings.SettingsService;
import com.vaabhi.shared.rupees.Rupees;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * GSTR-1 for a month, in the sections of the offline tool, and the same as CSV. Periods go by the date of issue;
 * cancelled invoices appear only in the document count. Credit notes count in the month they were issued: notes for
 * registered buyers go to CDNR, notes for large inter-state sales to CDNUR, and notes for small sales are netted into
 * B2CS.
 */
@Service
public class Gstr1Service {

    private static final BigDecimal B2CL_LIMIT = new BigDecimal("100000");
    private static final String HSN_DELIVERY = "9965";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final Map<String, String> STATES = IndianStates.all().stream()
            .collect(Collectors.toMap(IndianStates.State::code, IndianStates.State::name, (a, b) -> a));

    static final List<String> B2B_HEADERS = List.of(
            "GSTIN/UIN of Recipient",
            "Receiver Name",
            "Invoice Number",
            "Invoice date",
            "Invoice Value",
            "Place Of Supply",
            "Reverse Charge",
            "Applicable % of Tax Rate",
            "Invoice Type",
            "E-Commerce GSTIN",
            "Rate",
            "Taxable Value",
            "Cess Amount");
    static final List<String> B2CL_HEADERS = List.of(
            "Invoice Number",
            "Invoice date",
            "Invoice Value",
            "Place Of Supply",
            "Applicable % of Tax Rate",
            "Rate",
            "Taxable Value",
            "Cess Amount",
            "E-Commerce GSTIN");
    static final List<String> B2CS_HEADERS = List.of(
            "Type",
            "Place Of Supply",
            "Applicable % of Tax Rate",
            "Rate",
            "Taxable Value",
            "Cess Amount",
            "E-Commerce GSTIN");
    static final List<String> CDNR_HEADERS = List.of(
            "GSTIN/UIN of Recipient",
            "Receiver Name",
            "Note Number",
            "Note Date",
            "Note Type",
            "Place Of Supply",
            "Reverse Charge",
            "Note Supply Type",
            "Note Value",
            "Applicable % of Tax Rate",
            "Rate",
            "Taxable Value",
            "Cess Amount");
    static final List<String> CDNUR_HEADERS = List.of(
            "UR Type",
            "Note Number",
            "Note Date",
            "Note Type",
            "Place Of Supply",
            "Note Value",
            "Applicable % of Tax Rate",
            "Rate",
            "Taxable Value",
            "Cess Amount");
    static final List<String> HSN_HEADERS = List.of(
            "HSN",
            "Description",
            "UQC",
            "Total Quantity",
            "Total Value",
            "Rate",
            "Taxable Value",
            "Integrated Tax Amount",
            "Central Tax Amount",
            "State/UT Tax Amount",
            "Cess Amount");
    static final List<String> DOCS_HEADERS =
            List.of("Nature of Document", "Sr. No. From", "Sr. No. To", "Total Number", "Cancelled");

    /** One invoice (or credit note) at one rate: its lines at that rate, summed. */
    private record Entry(
            String number,
            LocalDate date,
            BigDecimal total,
            @Nullable String gstin,
            String buyer,
            @Nullable String place,
            boolean large,
            BigDecimal rate,
            BigDecimal taxable,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst) {

        boolean registered() {
            return gstin != null && !gstin.isBlank();
        }
    }

    private record Key(String place, BigDecimal rate) implements Comparable<Key> {
        @Override
        public int compareTo(Key other) {
            int byPlace = place.compareTo(other.place);
            return byPlace != 0 ? byPlace : rate.compareTo(other.rate);
        }
    }

    private final JdbcClient jdbc;
    private final SettingsService settings;

    Gstr1Service(JdbcClient jdbc, SettingsService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    // --- the month ----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Gstr1 month(YearMonth month) {
        var from = month.atDay(1);
        var to = month.atEndOfMonth();
        var invoices = entries("""
                FROM invoice i JOIN invoice_line l ON l.invoice_id = i.id
                WHERE i.cancelled_at IS NULL AND i.issued_on BETWEEN :from AND :to
                GROUP BY i.id, l.gst_rate ORDER BY i.number, l.gst_rate
                """, """
                i.number AS number, i.issued_on AS date, i.total_rupees AS total, i.client_gstin AS gstin,
                i.buyer_name AS buyer, COALESCE(i.place_of_supply, i.seller_state_code) AS place,
                (i.supply_type = 'INTER' AND i.total_rupees > :limit) AS large""", from, to);
        var notes = entries("""
                FROM credit_note n
                JOIN invoice i ON i.id = n.invoice_id
                JOIN credit_note_line l ON l.credit_note_id = n.id
                WHERE n.issued_on BETWEEN :from AND :to
                GROUP BY n.id, i.id, l.gst_rate ORDER BY n.number, l.gst_rate
                """, """
                n.number AS number, n.issued_on AS date, n.total_rupees AS total, i.client_gstin AS gstin,
                n.buyer_name AS buyer, COALESCE(n.place_of_supply, n.seller_state_code) AS place,
                (i.supply_type = 'INTER' AND i.total_rupees > :limit) AS large""", from, to);

        List<Gstr1.Gstr1Row> b2b = new ArrayList<>();
        List<Gstr1.Gstr1Row> b2cl = new ArrayList<>();
        Map<Key, BigDecimal[]> small = new TreeMap<>();
        for (var e : invoices) {
            if (e.registered()) {
                b2b.add(b2bRow(e));
            } else if (e.large()) {
                b2cl.add(b2clRow(e));
            } else {
                addSmall(small, e, 1);
            }
        }
        List<Gstr1.Gstr1Row> cdnr = new ArrayList<>();
        List<Gstr1.Gstr1Row> cdnur = new ArrayList<>();
        for (var e : notes) {
            if (e.registered()) {
                cdnr.add(cdnrRow(e));
            } else if (e.large()) {
                cdnur.add(cdnurRow(e));
            } else {
                addSmall(small, e, -1);
            }
        }
        List<Gstr1.Gstr1Row> b2cs = new ArrayList<>();
        small.forEach((key, sums) -> b2cs.add(b2csRow(key, sums)));

        return new Gstr1(
                month, settings.billing().gstApplies(), b2b, b2cl, b2cs, cdnr, cdnur, hsn(from, to), docs(from, to));
    }

    // Sonar java:S2077: the SQL is assembled only from fixed fragments in this class; every value is a bound parameter.
    @SuppressWarnings("java:S2077")
    private List<Entry> entries(String fromAndGroup, String select, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT %s,
                               l.gst_rate AS rate, SUM(l.taxable_rupees) AS taxable, SUM(l.cgst_rupees) AS cgst,
                               SUM(l.sgst_rupees) AS sgst, SUM(l.igst_rupees) AS igst
                        %s""".formatted(select, fromAndGroup))
                .param("from", from)
                .param("to", to)
                .param("limit", B2CL_LIMIT)
                .query((rs, rowNum) -> entry(rs))
                .list();
    }

    private static Entry entry(ResultSet rs) throws SQLException {
        return new Entry(
                rs.getString("number"),
                rs.getObject("date", LocalDate.class),
                rs.getBigDecimal("total"),
                rs.getString("gstin"),
                rs.getString("buyer"),
                rs.getString("place"),
                rs.getBoolean("large"),
                rs.getBigDecimal("rate"),
                rs.getBigDecimal("taxable"),
                rs.getBigDecimal("cgst"),
                rs.getBigDecimal("sgst"),
                rs.getBigDecimal("igst"));
    }

    // --- rows ---------------------------------------------------------------------------------------------------

    private static Gstr1.Gstr1Row row(List<String> headers, Entry e, BigDecimal value, Object... cells) {
        if (headers.size() != cells.length) {
            throw new IllegalStateException("Expected " + headers.size() + " columns but got " + cells.length);
        }
        Map<String, Object> columns = new LinkedHashMap<>();
        for (int i = 0; i < cells.length; i++) {
            columns.put(headers.get(i), cells[i]);
        }
        return new Gstr1.Gstr1Row(
                columns,
                Rupees.of(e.taxable()),
                Rupees.of(e.igst()),
                Rupees.of(e.cgst()),
                Rupees.of(e.sgst()),
                Rupees.of(value));
    }

    private static Gstr1.Gstr1Row b2bRow(Entry e) {
        return row(
                B2B_HEADERS,
                e,
                e.total(),
                e.gstin(),
                e.buyer(),
                e.number(),
                DATE.format(e.date()),
                Rupees.of(e.total()),
                place(e.place()),
                "N",
                "",
                "Regular B2B",
                "",
                Rupees.of(e.rate()),
                Rupees.of(e.taxable()),
                Rupees.ZERO);
    }

    private static Gstr1.Gstr1Row b2clRow(Entry e) {
        return row(
                B2CL_HEADERS,
                e,
                e.total(),
                e.number(),
                DATE.format(e.date()),
                Rupees.of(e.total()),
                place(e.place()),
                "",
                Rupees.of(e.rate()),
                Rupees.of(e.taxable()),
                Rupees.ZERO,
                "");
    }

    private static Gstr1.Gstr1Row cdnrRow(Entry e) {
        return row(
                CDNR_HEADERS,
                e,
                e.total(),
                e.gstin(),
                e.buyer(),
                e.number(),
                DATE.format(e.date()),
                "C",
                place(e.place()),
                "N",
                "Regular B2B",
                Rupees.of(e.total()),
                "",
                Rupees.of(e.rate()),
                Rupees.of(e.taxable()),
                Rupees.ZERO);
    }

    private static Gstr1.Gstr1Row cdnurRow(Entry e) {
        return row(
                CDNUR_HEADERS,
                e,
                e.total(),
                "B2CL",
                e.number(),
                DATE.format(e.date()),
                "C",
                place(e.place()),
                Rupees.of(e.total()),
                "",
                Rupees.of(e.rate()),
                Rupees.of(e.taxable()),
                Rupees.ZERO);
    }

    private static void addSmall(Map<Key, BigDecimal[]> small, Entry e, int sign) {
        var place = e.place();
        var sums = small.computeIfAbsent(new Key(place == null ? "" : place.trim(), Rupees.of(e.rate())), k ->
                new BigDecimal[] {Rupees.ZERO, Rupees.ZERO, Rupees.ZERO, Rupees.ZERO});
        var factor = BigDecimal.valueOf(sign);
        sums[0] = sums[0].add(e.taxable().multiply(factor));
        sums[1] = sums[1].add(e.cgst().multiply(factor));
        sums[2] = sums[2].add(e.sgst().multiply(factor));
        sums[3] = sums[3].add(e.igst().multiply(factor));
    }

    private static Gstr1.Gstr1Row b2csRow(Key key, BigDecimal[] sums) {
        Map<String, Object> columns = new LinkedHashMap<>();
        Object[] cells = {
            "OE", place(key.place()), "", key.rate(), Rupees.of(sums[0]), Rupees.ZERO, "",
        };
        for (int i = 0; i < cells.length; i++) {
            columns.put(B2CS_HEADERS.get(i), cells[i]);
        }
        return new Gstr1.Gstr1Row(
                columns, Rupees.of(sums[0]), Rupees.of(sums[3]), Rupees.of(sums[1]), Rupees.of(sums[2]), Rupees.ZERO);
    }

    /** "36-Telangana"; the bare code when the state is unknown, blank when there is no place. */
    private static String place(@Nullable String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        var trimmed = code.trim();
        var name = STATES.get(trimmed);
        return name == null ? trimmed : trimmed + "-" + name;
    }

    // --- HSN summary and documents ------------------------------------------------------------------------------

    private List<Gstr1.Gstr1HsnRow> hsn(LocalDate from, LocalDate to) {
        Map<String, String> names = new LinkedHashMap<>();
        jdbc.sql("SELECT hsn_code, name FROM tax_class ORDER BY id")
                .query()
                .listOfRows()
                .forEach(r -> names.putIfAbsent(String.valueOf(r.get("hsn_code")), String.valueOf(r.get("name"))));
        return jdbc.sql("""
                        SELECT hsn, rate, SUM(qty) AS qty, SUM(val) AS val, SUM(t) AS t, SUM(c) AS c, SUM(s) AS s,
                               SUM(g) AS g
                        FROM (
                            SELECT COALESCE(l.hsn_code, '') AS hsn, l.gst_rate AS rate, l.quantity AS qty,
                                   l.taxable_rupees + l.cgst_rupees + l.sgst_rupees + l.igst_rupees AS val,
                                   l.taxable_rupees AS t, l.cgst_rupees AS c, l.sgst_rupees AS s, l.igst_rupees AS g
                            FROM invoice_line l JOIN invoice i ON i.id = l.invoice_id
                            WHERE i.cancelled_at IS NULL AND i.issued_on BETWEEN :from AND :to
                            UNION ALL
                            SELECT COALESCE(l.hsn_code, ''), l.gst_rate, -l.quantity,
                                   -(l.taxable_rupees + l.cgst_rupees + l.sgst_rupees + l.igst_rupees),
                                   -l.taxable_rupees, -l.cgst_rupees, -l.sgst_rupees, -l.igst_rupees
                            FROM credit_note_line l JOIN credit_note n ON n.id = l.credit_note_id
                            WHERE n.issued_on BETWEEN :from AND :to
                        ) x
                        GROUP BY hsn, rate ORDER BY hsn, rate
                        """)
                .param("from", from)
                .param("to", to)
                .query((rs, rowNum) -> {
                    var code = rs.getString("hsn");
                    var description = HSN_DELIVERY.equals(code) ? "Delivery" : names.getOrDefault(code, "");
                    return new Gstr1.Gstr1HsnRow(
                            code,
                            description,
                            code.startsWith("99") ? "NA" : "PCS-PIECES",
                            rs.getLong("qty"),
                            Rupees.of(rs.getBigDecimal("val")),
                            Rupees.of(rs.getBigDecimal("rate")),
                            Rupees.of(rs.getBigDecimal("t")),
                            Rupees.of(rs.getBigDecimal("g")),
                            Rupees.of(rs.getBigDecimal("c")),
                            Rupees.of(rs.getBigDecimal("s")),
                            Rupees.ZERO);
                })
                .list();
    }

    private List<Gstr1.Gstr1DocRange> docs(LocalDate from, LocalDate to) {
        List<Gstr1.Gstr1DocRange> docs = new ArrayList<>();
        var invoices = docRange("""
                SELECT (array_agg(number ORDER BY issued_on, id))[1] AS first,
                       (array_agg(number ORDER BY issued_on DESC, id DESC))[1] AS last,
                       count(*) AS total, count(cancelled_at) AS cancelled
                FROM invoice WHERE issued_on BETWEEN :from AND :to
                """, "Invoices for outward supply", from, to);
        if (invoices != null) {
            docs.add(invoices);
        }
        var notes = docRange("""
                SELECT (array_agg(number ORDER BY issued_on, id))[1] AS first,
                       (array_agg(number ORDER BY issued_on DESC, id DESC))[1] AS last,
                       count(*) AS total, 0 AS cancelled
                FROM credit_note WHERE issued_on BETWEEN :from AND :to
                """, "Credit Note", from, to);
        if (notes != null) {
            docs.add(notes);
        }
        return docs;
    }

    private Gstr1.@Nullable Gstr1DocRange docRange(String sql, String nature, LocalDate from, LocalDate to) {
        var range = jdbc.sql(sql)
                .param("from", from)
                .param("to", to)
                .query((rs, rowNum) -> new Gstr1.Gstr1DocRange(
                        nature,
                        rs.getString("first"),
                        rs.getString("last"),
                        rs.getInt("total"),
                        rs.getInt("cancelled")))
                .single();
        return range.total() == 0 ? null : range;
    }

    // --- CSV ----------------------------------------------------------------------------------------------------

    /** One section as CSV (header line, then a line per row); 404 for a section that does not exist. */
    @Transactional(readOnly = true)
    public String csv(YearMonth month, String section) {
        var sectionName = section.toLowerCase(Locale.ROOT);
        if (!List.of("b2b", "b2cl", "b2cs", "cdnr", "cdnur", "hsn", "docs").contains(sectionName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No GSTR-1 section called " + section);
        }
        var gstr1 = month(month);
        var out = new StringBuilder();
        switch (sectionName) {
            case "b2b" -> rowsCsv(out, B2B_HEADERS, gstr1.b2b());
            case "b2cl" -> rowsCsv(out, B2CL_HEADERS, gstr1.b2cl());
            case "b2cs" -> rowsCsv(out, B2CS_HEADERS, gstr1.b2cs());
            case "cdnr" -> rowsCsv(out, CDNR_HEADERS, gstr1.cdnr());
            case "cdnur" -> rowsCsv(out, CDNUR_HEADERS, gstr1.cdnur());
            case "hsn" -> {
                line(out, HSN_HEADERS);
                for (var h : gstr1.hsn()) {
                    line(
                            out,
                            List.of(
                                    h.hsn(),
                                    h.description(),
                                    h.uqc(),
                                    h.quantity(),
                                    h.value(),
                                    h.rate(),
                                    h.taxable(),
                                    h.igst(),
                                    h.cgst(),
                                    h.sgst(),
                                    h.cess()));
                }
            }
            default -> {
                line(out, DOCS_HEADERS);
                for (var d : gstr1.docs()) {
                    line(out, List.of(d.nature(), d.from(), d.to(), d.total(), d.cancelled()));
                }
            }
        }
        return out.toString();
    }

    private static void rowsCsv(StringBuilder out, List<String> headers, List<Gstr1.Gstr1Row> rows) {
        line(out, headers);
        for (var row : rows) {
            line(out, List.copyOf(row.columns().values()));
        }
    }

    private static void line(StringBuilder out, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(field(cells.get(i)));
        }
        out.append('\n');
    }

    private static String field(Object cell) {
        var text = cell instanceof BigDecimal d ? d.toPlainString() : cell.toString();
        // Numbers (credit netting can make them negative) stay as they are; text that a spreadsheet would read as a
        // formula gets a leading quote.
        if (!(cell instanceof Number) && !text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
