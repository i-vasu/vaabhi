package com.vaabhi.accounts;

import com.vaabhi.shared.rupees.Rupees;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Imports a Shiprocket COD remittance report: preview, saved mapping, grouping by ref, and the list. */
@Service
public class CodRemittanceService {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int PREVIEW_ROWS = 20;
    private static final int FILE_NAME_MAX = 200;
    private static final int AWB_MAX = 40;
    private static final int ORDER_NUMBER_MAX = 40;
    private static final String KIND = "COD_REMITTANCE";

    private final JdbcClient jdbc;
    private final ImportProfileService profiles;

    CodRemittanceService(JdbcClient jdbc, ImportProfileService profiles) {
        this.jdbc = jdbc;
        this.profiles = profiles;
    }

    /** The first rows of the file, so the owner can check the column mapping before importing. */
    @Transactional(readOnly = true)
    public FilePreview preview(String fileName, byte[] bytes) {
        checkSize(bytes);
        var rows = TabularFile.read(fileName, new ByteArrayInputStream(bytes));
        return new FilePreview(
                rows.size() > PREVIEW_ROWS ? rows.subList(0, PREVIEW_ROWS) : rows, profiles.latest(KIND));
    }

    /** Saves the profile and each new remittance with its AWB lines. Refs already imported are skipped. */
    @Transactional
    public CodImportSummary importReport(
            String fileName, byte[] bytes, ColumnMapping mapping, @Nullable String actor) {
        require(mapping, "remittanceRef", "remittance ref");
        require(mapping, "remittedOn", "remittance date");
        require(mapping, "awb", "AWB");
        require(mapping, "cod", "COD amount");
        checkSize(bytes);
        var rows = TabularFile.read(fileName, new ByteArrayInputStream(bytes));
        var skipped = new ArrayList<SkippedFileRow>();
        var groups = new LinkedHashMap<String, List<Row>>();
        Map<String, Integer> columns = mapping.columns() == null ? Map.of() : mapping.columns();
        boolean deductionMapped = columns.containsKey("deduction");
        boolean netMapped = columns.containsKey("net");
        for (int index = mapping.headerRow() + 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            int rowNumber = index + 1;
            if (blank(row)) {
                continue;
            }
            LocalDate remittedOn = BankFileParser.parseDate(cell(row, columns, "remittedOn"), mapping.dateFormat());
            if (remittedOn == null) {
                skipped.add(new SkippedFileRow(rowNumber, "no date"));
                continue;
            }
            String ref = cell(row, columns, "remittanceRef").trim();
            if (ref.isEmpty()) {
                skipped.add(new SkippedFileRow(rowNumber, "no remittance ref"));
                continue;
            }
            String awb = cell(row, columns, "awb").trim();
            if (awb.isEmpty()) {
                skipped.add(new SkippedFileRow(rowNumber, "no AWB"));
                continue;
            }
            String utr = cell(row, columns, "utr").trim();
            String orderNumber = cell(row, columns, "orderNumber").trim();
            groups.computeIfAbsent(ref, key -> new ArrayList<>())
                    .add(new Row(
                            remittedOn,
                            utr.isEmpty() ? null : utr,
                            awb,
                            orderNumber.isEmpty() ? null : orderNumber,
                            BankFileParser.parseAmount(cell(row, columns, "cod")).abs(),
                            deductionMapped
                                    ? BankFileParser.parseAmount(cell(row, columns, "deduction"))
                                            .abs()
                                    : Rupees.ZERO,
                            netMapped
                                    ? BankFileParser.parseAmount(cell(row, columns, "net")).abs()
                                    : null));
        }
        if (groups.isEmpty()) {
            throw new IllegalArgumentException(
                    "No remittance rows were found. Check the header row and date format.");
        }
        profiles.save(KIND, mapping);
        int added = 0;
        int duplicates = 0;
        var unknownAwbs = new ArrayList<String>();
        for (var group : groups.entrySet()) {
            String ref = group.getKey();
            var groupRows = group.getValue();
            boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM cod_remittance WHERE remittance_ref = :ref)")
                    .param("ref", ref)
                    .query(Boolean.class)
                    .single();
            if (exists) {
                duplicates++;
                continue;
            }
            BigDecimal cod = Rupees.sum(groupRows, Row::cod);
            BigDecimal deduction;
            if (deductionMapped) {
                deduction = Rupees.sum(groupRows, Row::deduction);
            } else if (netMapped) {
                deduction = Rupees.max(cod.subtract(Rupees.sum(groupRows, Row::net)), Rupees.ZERO);
            } else {
                deduction = Rupees.ZERO;
            }
            if (deduction.compareTo(cod) > 0) {
                throw new IllegalArgumentException("Remittance " + ref + " deducts more than its COD amount");
            }
            BigDecimal net = cod.subtract(deduction);
            var first = groupRows.getFirst();
            long id = jdbc.sql("""
                            INSERT INTO cod_remittance (remittance_ref, utr, remitted_on, cod_rupees,
                                                        deduction_rupees, net_rupees, file_name, uploaded_by)
                            VALUES (:ref, :utr, :on, :cod, :deduction, :net, :fileName, :by)
                            RETURNING id
                            """)
                    .param("ref", ref)
                    .param("utr", first.utr())
                    .param("on", first.remittedOn())
                    .param("cod", cod)
                    .param("deduction", deduction)
                    .param("net", net)
                    .param("fileName", truncate(fileName, FILE_NAME_MAX))
                    .param("by", actor)
                    .query(Long.class)
                    .single();
            for (var row : groupRows) {
                Long webOrderId = jdbc.sql("SELECT id FROM web_order WHERE tracking_number = :awb LIMIT 1")
                        .param("awb", row.awb())
                        .query(Long.class)
                        .optional()
                        .orElse(null);
                if (webOrderId == null) {
                    unknownAwbs.add(row.awb());
                }
                jdbc.sql("""
                                INSERT INTO cod_remittance_line (remittance_id, awb, order_number, cod_rupees,
                                                                 web_order_id)
                                VALUES (:remittance, :awb, :orderNumber, :cod, :webOrder)
                                """)
                        .param("remittance", id)
                        .param("awb", truncate(row.awb(), AWB_MAX))
                        .param("orderNumber", truncate(row.orderNumber(), ORDER_NUMBER_MAX))
                        .param("cod", row.cod())
                        .param("webOrder", webOrderId)
                        .update();
            }
            added++;
        }
        return new CodImportSummary(added, duplicates, unknownAwbs, skipped);
    }

    /** Every remittance, newest first, with its AWB count and posted entry number. */
    @Transactional(readOnly = true)
    public List<CodRemittanceView> list() {
        return jdbc.sql("""
                        SELECT r.id, r.remittance_ref, r.utr, r.remitted_on, r.cod_rupees, r.deduction_rupees,
                               r.net_rupees,
                               (SELECT COUNT(*) FROM cod_remittance_line l WHERE l.remittance_id = r.id)
                                   AS awb_count,
                               j.number AS journal_number
                        FROM cod_remittance r
                        LEFT JOIN journal_entry j
                               ON j.source_type = 'COD_REMITTANCE' AND j.source_step = 'SETTLE'
                              AND j.source_id = r.id
                        ORDER BY r.remitted_on DESC, r.id DESC
                        LIMIT 200
                        """)
                .query(CodRemittanceView.class)
                .list();
    }

    private static void require(ColumnMapping mapping, String key, String label) {
        Integer index = mapping.columns() == null ? null : mapping.columns().get(key);
        if (index == null) {
            throw new IllegalArgumentException("Map the " + label + " column");
        }
    }

    private static void checkSize(byte[] bytes) {
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("File is larger than 5 MB");
        }
    }

    private static boolean blank(List<String> row) {
        return row == null || row.stream().allMatch(value -> value == null || value.isBlank());
    }

    private static String cell(List<String> row, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value;
    }

    private static @Nullable String truncate(@Nullable String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    /** One row of the report, before grouping. {@code net} is null unless its column is mapped. */
    private record Row(
            LocalDate remittedOn,
            @Nullable String utr,
            String awb,
            @Nullable String orderNumber,
            BigDecimal cod,
            BigDecimal deduction,
            @Nullable BigDecimal net) {}
}
