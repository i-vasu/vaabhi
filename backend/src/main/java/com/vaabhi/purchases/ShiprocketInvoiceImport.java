package com.vaabhi.purchases;

import com.vaabhi.accounts.BankFileParser;
import com.vaabhi.accounts.BooksSettingsService;
import com.vaabhi.accounts.ColumnMapping;
import com.vaabhi.accounts.FilePreview;
import com.vaabhi.accounts.ImportProfileService;
import com.vaabhi.accounts.SkippedFileRow;
import com.vaabhi.accounts.TabularFile;
import com.vaabhi.settings.IndianStates;
import com.vaabhi.shared.rupees.Rupees;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Imports Shiprocket freight invoices as suppliers' bills: preview, saved mapping, grouping by invoice number, and
 * skipping invoices already billed. The GST comes from the file rather than from a rate, so the bills are written
 * directly; nothing here posts to the journal.
 */
@Service
public class ShiprocketInvoiceImport {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int PREVIEW_ROWS = 20;
    private static final int HSN_COURIER = 996812;
    private static final String KIND = "SHIPROCKET_INVOICE";
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcClient jdbc;
    private final ImportProfileService profiles;
    private final BooksSettingsService settings;
    private final CompanyState companyState;

    ShiprocketInvoiceImport(
            JdbcClient jdbc, ImportProfileService profiles, BooksSettingsService settings, CompanyState companyState) {
        this.jdbc = jdbc;
        this.profiles = profiles;
        this.settings = settings;
        this.companyState = companyState;
    }

    /** The first rows of the file, so the owner can check the column mapping before importing. */
    @Transactional(readOnly = true)
    public FilePreview preview(MultipartFile file) {
        var rows = TabularFile.read(fileName(file), new ByteArrayInputStream(bytes(file)));
        return new FilePreview(
                rows.size() > PREVIEW_ROWS ? rows.subList(0, PREVIEW_ROWS) : rows, profiles.latest(KIND));
    }

    /** Saves the profile and a bill for each new invoice, summing the rows of one invoice. */
    @Transactional
    public ShiprocketInvoiceSummary importFile(MultipartFile file, ColumnMapping mapping, @Nullable String actor) {
        long partyId = shiprocketParty();
        require(mapping, "invoiceNo", "invoice number");
        require(mapping, "invoiceDate", "invoice date");
        require(mapping, "taxable", "taxable amount");
        var rows = TabularFile.read(fileName(file), new ByteArrayInputStream(bytes(file)));
        Map<String, Integer> columns = mapping.columns() == null ? Map.of() : mapping.columns();
        boolean splitMapped =
                columns.containsKey("cgst") || columns.containsKey("sgst") || columns.containsKey("igst");
        boolean gstMapped = columns.containsKey("gst");
        boolean totalMapped = columns.containsKey("total");

        var badRows = new ArrayList<SkippedFileRow>();
        var groups = new LinkedHashMap<String, List<Row>>();
        for (int index = mapping.headerRow() + 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            int rowNumber = index + 1;
            if (blank(row)) {
                continue;
            }
            LocalDate invoiceDate = BankFileParser.parseDate(cell(row, columns, "invoiceDate"), mapping.dateFormat());
            if (invoiceDate == null) {
                badRows.add(new SkippedFileRow(rowNumber, "no date"));
                continue;
            }
            String invoiceNo = cell(row, columns, "invoiceNo").trim();
            if (invoiceNo.isEmpty()) {
                badRows.add(new SkippedFileRow(rowNumber, "no invoice number"));
                continue;
            }
            groups.computeIfAbsent(invoiceNo.toLowerCase(Locale.ROOT), key -> new ArrayList<>())
                    .add(new Row(
                            rowNumber,
                            invoiceDate,
                            invoiceNo,
                            BankFileParser.parseAmount(cell(row, columns, "taxable")),
                            gstMapped ? BankFileParser.parseAmount(cell(row, columns, "gst")) : Rupees.ZERO,
                            splitMapped ? BankFileParser.parseAmount(cell(row, columns, "cgst")) : Rupees.ZERO,
                            splitMapped ? BankFileParser.parseAmount(cell(row, columns, "sgst")) : Rupees.ZERO,
                            splitMapped ? BankFileParser.parseAmount(cell(row, columns, "igst")) : Rupees.ZERO,
                            totalMapped ? BankFileParser.parseAmount(cell(row, columns, "total")) : Rupees.ZERO));
        }
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("No invoice rows were found. Check the header row and date format.");
        }
        profiles.save(KIND, mapping);

        var supplierState = jdbc.sql("SELECT state FROM party WHERE id = :id")
                .param("id", partyId)
                .query(PartyStateRow.class)
                .single()
                .state();
        var supplyStateCode = supplierState == null ? null : IndianStates.code(supplierState).orElse(null);
        boolean interState = BillMath.interState(supplyStateCode, companyState.code());
        long courierAccount = jdbc.sql("SELECT id FROM account WHERE system_key = 'EXPENSE_COURIER'")
                .query(Long.class)
                .single();

        int created = 0;
        int duplicates = 0;
        var skippedInvoiceNos = new ArrayList<String>();
        var totalRupees = Rupees.ZERO;
        for (var group : groups.values()) {
            var first = group.getFirst();
            boolean exists = jdbc.sql("""
                            SELECT EXISTS (SELECT 1 FROM supplier_bill
                                           WHERE party_id = :party AND lower(bill_no) = lower(:billNo))
                            """)
                    .param("party", partyId)
                    .param("billNo", first.invoiceNo())
                    .query(Boolean.class)
                    .single();
            if (exists) {
                duplicates++;
                skippedInvoiceNos.add(first.invoiceNo());
                continue;
            }
            var taxable = Rupees.sum(group, Row::taxable);
            if (taxable.signum() <= 0) {
                badRows.add(new SkippedFileRow(first.rowNumber(), "No freight amount"));
                continue;
            }
            BigDecimal cgst;
            BigDecimal sgst;
            BigDecimal igst;
            if (splitMapped) {
                cgst = Rupees.sum(group, Row::cgst);
                sgst = Rupees.sum(group, Row::sgst);
                igst = Rupees.sum(group, Row::igst);
            } else if (gstMapped) {
                var gst = Rupees.sum(group, Row::gst);
                if (interState) {
                    cgst = Rupees.ZERO;
                    sgst = Rupees.ZERO;
                    igst = gst;
                } else {
                    cgst = gst.divide(TWO, Rupees.SCALE, RoundingMode.FLOOR);
                    sgst = gst.subtract(cgst);
                    igst = Rupees.ZERO;
                }
            } else {
                cgst = Rupees.ZERO;
                sgst = Rupees.ZERO;
                igst = Rupees.ZERO;
            }
            var tax = Rupees.of(cgst.add(sgst).add(igst));
            // GST above 28% of the freight means the columns are mapped wrong; the line's CHECK would reject it.
            if (tax.compareTo(taxable.multiply(BigDecimal.valueOf(28)).divide(HUNDRED)) > 0) {
                badRows.add(new SkippedFileRow(first.rowNumber(), "GST is more than 28% of the freight"));
                continue;
            }
            var total = Rupees.of(taxable.add(tax));
            if (totalMapped) {
                var declared = Rupees.sum(group, Row::total);
                if (declared.subtract(total).abs().compareTo(BigDecimal.ONE) > 0) {
                    badRows.add(new SkippedFileRow(first.rowNumber(), "Total does not match taxable + GST"));
                    continue;
                }
            }
            long billId = jdbc.sql("""
                            INSERT INTO supplier_bill (party_id, bill_no, bill_date, due_date, supply_state_code,
                                                       taxable_rupees, tax_rupees, total_rupees, tds_rupees,
                                                       payable_rupees, source, created_by)
                            VALUES (:party, :billNo, :billDate, :billDate, :state, :taxable, :tax, :total, 0,
                                    :total, 'SHIPROCKET', :by)
                            RETURNING id
                            """)
                    .param("party", partyId)
                    .param("billNo", first.invoiceNo())
                    .param("billDate", first.invoiceDate())
                    .param("state", supplyStateCode)
                    .param("taxable", taxable)
                    .param("tax", tax)
                    .param("total", total)
                    .param("by", actor)
                    .query(Long.class)
                    .single();
            jdbc.sql("""
                            INSERT INTO supplier_bill_line (bill_id, position, kind, description, account_id, hsn,
                                                            taxable_rupees, gst_rate_percent, cgst_rupees,
                                                            sgst_rupees, igst_rupees, total_rupees)
                            VALUES (:bill, 1, 'EXPENSE', :description, :account, :hsn, :taxable, :rate, :cgst,
                                    :sgst, :igst, :total)
                            """)
                    .param("bill", billId)
                    .param("description", "Shiprocket freight " + first.invoiceNo())
                    .param("account", courierAccount)
                    .param("hsn", String.valueOf(HSN_COURIER))
                    .param("taxable", taxable)
                    .param("rate", tax.multiply(HUNDRED).divide(taxable, Rupees.SCALE, RoundingMode.HALF_UP))
                    .param("cgst", cgst)
                    .param("sgst", sgst)
                    .param("igst", igst)
                    .param("total", total)
                    .update();
            created++;
            totalRupees = totalRupees.add(total);
        }
        return new ShiprocketInvoiceSummary(
                created, duplicates, skippedInvoiceNos, badRows, Rupees.of(totalRupees));
    }

    private long shiprocketParty() {
        var partyId = settings.shiprocketPartyId();
        if (partyId == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Pick the Shiprocket supplier in Books settings first");
        }
        return partyId;
    }

    private static String fileName(MultipartFile file) {
        var name = file.getOriginalFilename();
        return name == null ? "freight" : name;
    }

    private static byte[] bytes(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the file", e);
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("File is larger than 5 MB");
        }
        return bytes;
    }

    private static void require(ColumnMapping mapping, String key, String label) {
        Integer index = mapping.columns() == null ? null : mapping.columns().get(key);
        if (index == null) {
            throw new IllegalArgumentException("Map the " + label + " column");
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

    private record PartyStateRow(@Nullable String state) {}

    /** One data row of the report, before grouping; amounts whose column is not mapped stay zero. */
    private record Row(
            int rowNumber,
            LocalDate invoiceDate,
            String invoiceNo,
            BigDecimal taxable,
            BigDecimal gst,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal total) {}
}
