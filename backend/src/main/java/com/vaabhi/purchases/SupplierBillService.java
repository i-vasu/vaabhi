package com.vaabhi.purchases;

import com.vaabhi.inventory.InventoryService;
import com.vaabhi.inventory.StockMovement;
import com.vaabhi.settings.IndianStates;
import com.vaabhi.shared.rupees.Rupees;
import com.vaabhi.shared.storage.ObjectStorage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Supplier bills: typing one, editing it until the books hold it, cancelling it, attaching the paper and suggesting
 * TDS. Stock bought on a bill lands in inventory and an asset bought on one joins the fixed-asset register. Nothing
 * here posts to the journal.
 */
@Service
public class SupplierBillService {

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final String PREFIX = "bills/";

    private static final String BILL = """
            SELECT b.id, b.party_id, p.name AS supplier_name, b.bill_no, b.bill_date, b.due_date,
                   b.supply_state_code, b.taxable_rupees,
                   COALESCE(l.cgst_rupees, 0) AS cgst_rupees, COALESCE(l.sgst_rupees, 0) AS sgst_rupees,
                   COALESCE(l.igst_rupees, 0) AS igst_rupees, b.total_rupees, b.tds_section_id, b.tds_rupees,
                   b.payable_rupees, COALESCE(a.amount_rupees, 0) AS paid_rupees, b.source, b.status,
                   b.attachment_key, b.notes
            FROM supplier_bill b
            JOIN party p ON p.id = b.party_id
            LEFT JOIN (SELECT bill_id, SUM(cgst_rupees) AS cgst_rupees, SUM(sgst_rupees) AS sgst_rupees,
                              SUM(igst_rupees) AS igst_rupees
                       FROM supplier_bill_line GROUP BY bill_id) l ON l.bill_id = b.id
            LEFT JOIN (SELECT bill_id, SUM(amount_rupees) AS amount_rupees
                       FROM supplier_payment_allocation GROUP BY bill_id) a ON a.bill_id = b.id
            """;

    private static final String LINE = """
            SELECT l.id, l.position, l.kind, l.description, l.account_id, a.name AS account_name,
                   l.stock_item_id, i.name AS stock_item_name, l.quantity, l.asset_class_id,
                   c.name AS asset_class_name, fa.id AS fixed_asset_id, l.hsn, l.taxable_rupees,
                   l.gst_rate_percent, l.cgst_rupees, l.sgst_rupees, l.igst_rupees, l.total_rupees
            FROM supplier_bill_line l
            JOIN account a ON a.id = l.account_id
            LEFT JOIN stock_item i ON i.id = l.stock_item_id
            LEFT JOIN asset_class c ON c.id = l.asset_class_id
            LEFT JOIN fixed_asset fa ON fa.bill_line_id = l.id
            """;

    private final JdbcClient jdbc;
    private final InventoryService inventory;
    private final CompanyState companyState;
    private final ObjectStorage storage;
    private final BooksPostedCheck posted;

    SupplierBillService(
            JdbcClient jdbc,
            InventoryService inventory,
            CompanyState companyState,
            ObjectStorage storage,
            BooksPostedCheck posted) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.companyState = companyState;
        this.storage = storage;
        this.posted = posted;
    }

    private record SupplierRow(
            String name, @Nullable String state, @Nullable String pan, @Nullable Long tdsSectionId) {}

    private record BillRow(
            long id,
            long partyId,
            String supplierName,
            String billNo,
            LocalDate billDate,
            @Nullable LocalDate dueDate,
            @Nullable String supplyStateCode,
            BigDecimal taxableRupees,
            BigDecimal cgstRupees,
            BigDecimal sgstRupees,
            BigDecimal igstRupees,
            BigDecimal totalRupees,
            @Nullable Long tdsSectionId,
            BigDecimal tdsRupees,
            BigDecimal payableRupees,
            BigDecimal paidRupees,
            String source,
            String status,
            @Nullable String attachmentKey,
            @Nullable String notes) {}

    private record AssetClassRow(long assetAccountId) {}

    private record OldLineRow(String kind, @Nullable Long stockItemId, @Nullable BigDecimal quantity) {}

    private record AssetRow(long id, @Nullable LocalDate disposedOn) {}

    private record TdsSectionRow(
            long id,
            String code,
            BigDecimal ratePercent,
            BigDecimal noPanRatePercent,
            @Nullable BigDecimal yearThresholdRupees,
            @Nullable BigDecimal singleThresholdRupees) {}

    private record LineCalc(
            SupplierBillLineForm form, long accountId, BigDecimal taxable, BillMath.LineTax tax) {}

    private record Totals(BigDecimal taxable, BigDecimal tax) {}

    /** Records a bill and its lines; stock and assets come along with it. */
    @Transactional
    public SupplierBillView create(SupplierBillForm form, @Nullable String actor) {
        var party = supplier(form.partyId());
        var billNo = form.billNo().trim();
        checkDueDate(form.billDate(), form.dueDate());
        var supplyState = supplyStateCode(form.supplyStateCode(), party.state());
        var interState = BillMath.interState(supplyState, companyState.code());
        var lines = prepareLines(form.lines(), interState);
        var totals = totals(lines);
        var tdsRupees = Rupees.of(form.tdsRupees());
        var tdsSection =
                resolveTdsSection(form.tdsSectionId(), party.tdsSectionId(), tdsRupees, totals.taxable());
        var total = Rupees.of(totals.taxable().add(totals.tax()));
        var payable = Rupees.of(total.subtract(tdsRupees));
        long id;
        try {
            id = insertBill(form, billNo, supplyState, totals, total, tdsSection, tdsRupees, payable, actor);
        } catch (DuplicateKeyException e) {
            throw duplicate(billNo, e);
        }
        var lineIds = insertLines(id, lines);
        applyStockAndAssets(form.billDate(), billNo, lines, lineIds, actor);
        checkPaidNow(form);
        return view(id, warning(id, form.partyId(), form.billDate(), totals.taxable(), tdsRupees));
    }

    /** Rewrites an open bill that the books do not hold yet, moving stock by the difference. */
    @Transactional
    public SupplierBillView update(long id, SupplierBillForm form, @Nullable String actor) {
        var current = billRow(id);
        if (posted.posted("SUPPLIER_BILL", id, "ISSUE")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This bill is in the books; cancel it instead");
        }
        if ("CANCELLED".equals(current.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This bill is cancelled");
        }
        var party = supplier(form.partyId());
        var billNo = form.billNo().trim();
        checkDueDate(form.billDate(), form.dueDate());
        var supplyState = supplyStateCode(form.supplyStateCode(), party.state());
        var interState = BillMath.interState(supplyState, companyState.code());
        var lines = prepareLines(form.lines(), interState);
        var totals = totals(lines);
        var tdsRupees = Rupees.of(form.tdsRupees());
        var tdsSection =
                resolveTdsSection(form.tdsSectionId(), party.tdsSectionId(), tdsRupees, totals.taxable());
        var total = Rupees.of(totals.taxable().add(totals.tax()));
        var payable = Rupees.of(total.subtract(tdsRupees));

        reverseStock(id, current.billNo(), actor);
        removeAssets(id, "This bill's asset already has depreciation in the books");
        jdbc.sql("DELETE FROM supplier_bill_line WHERE bill_id = :bill")
                .param("bill", id)
                .update();
        try {
            jdbc.sql("""
                            UPDATE supplier_bill
                            SET party_id = :party, bill_no = :billNo, bill_date = :billDate, due_date = :dueDate,
                                supply_state_code = :state, taxable_rupees = :taxable, tax_rupees = :tax,
                                total_rupees = :total, tds_section_id = :section, tds_rupees = :tds,
                                payable_rupees = :payable, notes = :notes
                            WHERE id = :id
                            """)
                    .param("party", form.partyId())
                    .param("billNo", billNo)
                    .param("billDate", form.billDate())
                    .param("dueDate", form.dueDate())
                    .param("state", supplyState)
                    .param("taxable", totals.taxable())
                    .param("tax", totals.tax())
                    .param("total", total)
                    .param("section", tdsSection)
                    .param("tds", tdsRupees)
                    .param("payable", payable)
                    .param("notes", blankToNull(form.notes()))
                    .param("id", id)
                    .update();
        } catch (DuplicateKeyException e) {
            throw duplicate(billNo, e);
        }
        var lineIds = insertLines(id, lines);
        applyStockAndAssets(form.billDate(), billNo, lines, lineIds, actor);
        checkPaidNow(form);
        return view(id, warning(id, form.partyId(), form.billDate(), totals.taxable(), tdsRupees));
    }

    /** Cancels an open bill: its stock goes back and an asset bought on it leaves the register. */
    @Transactional
    public SupplierBillView cancel(long id, LocalDate on, String reason, @Nullable String actor) {
        var current = billRow(id);
        if ("CANCELLED".equals(current.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This bill is already cancelled");
        }
        boolean allocated = jdbc.sql("SELECT EXISTS (SELECT 1 FROM supplier_payment_allocation WHERE bill_id = :bill)")
                .param("bill", id)
                .query(Boolean.class)
                .single();
        if (allocated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Remove the payments on this bill first");
        }
        removeAssets(id, "Dispose the asset first");
        reverseStock(id, current.billNo(), actor);
        jdbc.sql("""
                        UPDATE supplier_bill
                        SET status = 'CANCELLED', cancelled_on = :on, cancel_reason = :reason
                        WHERE id = :id
                        """)
                .param("on", on)
                .param("reason", reason.trim())
                .param("id", id)
                .update();
        return view(id, null);
    }

    @Transactional(readOnly = true)
    public SupplierBillView get(long id) {
        return view(id, null);
    }

    /** Bills newest first, optionally of one supplier, status, month or source. */
    @Transactional(readOnly = true)
    public List<SupplierBillListEntry> list(
            @Nullable Long partyId,
            @Nullable String status,
            @Nullable YearMonth month,
            @Nullable String source) {
        var monthStart = month == null ? null : month.atDay(1);
        var monthEnd = month == null ? null : month.plusMonths(1).atDay(1);
        return jdbc.sql("""
                        SELECT b.id, p.name AS supplier_name, b.bill_no, b.bill_date, b.due_date, b.total_rupees,
                               b.payable_rupees, COALESCE(a.amount_rupees, 0) AS paid_rupees, b.status, b.source,
                               EXISTS (SELECT 1 FROM journal_entry j
                                       WHERE j.source_type = 'SUPPLIER_BILL' AND j.source_id = b.id
                                         AND j.source_step = 'ISSUE') AS posted
                        FROM supplier_bill b
                        JOIN party p ON p.id = b.party_id
                        LEFT JOIN (SELECT bill_id, SUM(amount_rupees) AS amount_rupees
                                   FROM supplier_payment_allocation GROUP BY bill_id) a ON a.bill_id = b.id
                        WHERE (CAST(:party AS bigint) IS NULL OR b.party_id = :party)
                          AND (CAST(:status AS varchar) IS NULL OR b.status = :status)
                          AND (CAST(:source AS varchar) IS NULL OR b.source = :source)
                          AND (CAST(:monthStart AS date) IS NULL
                               OR (b.bill_date >= :monthStart AND b.bill_date < :monthEnd))
                        ORDER BY b.bill_date DESC, b.id DESC
                        """)
                .param("party", partyId)
                .param("status", blankToNull(status))
                .param("source", blankToNull(source))
                .param("monthStart", monthStart)
                .param("monthEnd", monthEnd)
                .query(SupplierBillListEntry.class)
                .list();
    }

    /** A presigned URL for a PDF or photo of the bill; the key is confirmed once the browser has uploaded it. */
    @Transactional(readOnly = true)
    public AttachmentUpload startAttachment(long id, String contentType) {
        billRow(id);
        if (!storage.configured()) {
            throw new ErrorResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemDetail.forStatusAndDetail(
                            HttpStatus.SERVICE_UNAVAILABLE, "File storage is not set up on this server"),
                    null);
        }
        var extension = switch (contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT)) {
            case "application/pdf" -> "pdf";
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attach a PDF or photo");
        };
        var key = PREFIX + id + "/" + UUID.randomUUID() + "." + extension;
        return new AttachmentUpload(key, storage.presignPut(key, WINDOW).toString());
    }

    @Transactional
    public void confirmAttachment(long id, String key) {
        billRow(id);
        if (key == null || !key.startsWith(PREFIX + id + "/") || !storage.exists(key)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload the file first");
        }
        jdbc.sql("UPDATE supplier_bill SET attachment_key = :key WHERE id = :id")
                .param("key", key)
                .param("id", id)
                .update();
    }

    /**
     * What TDS the supplier's section suggests for a bill of {@code billTaxable}: nothing until the bill or the
     * supplier's bills this financial year cross a threshold.
     */
    @Transactional(readOnly = true)
    public TdsSuggestion suggestTds(
            long partyId, LocalDate billDate, BigDecimal billTaxable, @Nullable Long excludeBillId) {
        var supplier = supplier(partyId);
        if (supplier.tdsSectionId() == null) {
            return new TdsSuggestion(null, null, Rupees.ZERO, Rupees.ZERO, false, null);
        }
        var section = jdbc.sql("""
                        SELECT id, code, rate_percent, no_pan_rate_percent, year_threshold_rupees,
                               single_threshold_rupees
                        FROM tds_section WHERE id = :id
                        """)
                .param("id", supplier.tdsSectionId())
                .query(TdsSectionRow.class)
                .single();
        var rate = blankToNull(supplier.pan()) == null ? section.noPanRatePercent() : section.ratePercent();
        var fyStart = BillMath.fyStart(billDate);
        var prior = jdbc.sql("""
                        SELECT COALESCE(SUM(taxable_rupees), 0)
                        FROM supplier_bill
                        WHERE party_id = :party AND status = 'OPEN'
                          AND bill_date >= :from AND bill_date < :to
                          AND (CAST(:exclude AS bigint) IS NULL OR id <> :exclude)
                        """)
                .param("party", partyId)
                .param("from", fyStart)
                .param("to", fyStart.plusYears(1))
                .param("exclude", excludeBillId)
                .query(BigDecimal.class)
                .single();
        var thisYear = Rupees.of(prior.add(billTaxable));
        var yearCrossed =
                section.yearThresholdRupees() != null && thisYear.compareTo(section.yearThresholdRupees()) > 0;
        var singleCrossed = section.singleThresholdRupees() != null
                && billTaxable.compareTo(section.singleThresholdRupees()) > 0;
        var crossed = yearCrossed || singleCrossed;
        var suggested = crossed ? BillMath.tds(billTaxable, rate) : Rupees.ZERO;
        var threshold = yearCrossed ? section.yearThresholdRupees() : section.singleThresholdRupees();
        var warning = crossed
                ? "TDS under " + section.code() + " is due: this supplier's bills this year cross ₹"
                        + threshold.stripTrailingZeros().toPlainString()
                : null;
        return new TdsSuggestion(section.id(), section.code(), Rupees.of(rate), suggested, crossed, warning);
    }

    private SupplierRow supplier(long partyId) {
        return jdbc.sql("SELECT name, state, pan, tds_section_id FROM party WHERE id = :id AND is_supplier")
                .param("id", partyId)
                .query(SupplierRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a supplier"));
    }

    private BillRow billRow(long id) {
        return jdbc.sql(BILL + " WHERE b.id = :id")
                .param("id", id)
                .query(BillRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found"));
    }

    private SupplierBillView view(long id, @Nullable String warning) {
        var row = billRow(id);
        var lines = jdbc.sql(LINE + " WHERE l.bill_id = :bill ORDER BY l.position")
                .param("bill", id)
                .query(SupplierBillLineView.class)
                .list();
        return new SupplierBillView(
                row.id(),
                row.partyId(),
                row.supplierName(),
                row.billNo(),
                row.billDate(),
                row.dueDate(),
                row.supplyStateCode(),
                row.taxableRupees(),
                row.cgstRupees(),
                row.sgstRupees(),
                row.igstRupees(),
                row.totalRupees(),
                row.tdsSectionId(),
                row.tdsRupees(),
                row.payableRupees(),
                row.paidRupees(),
                row.source(),
                row.status(),
                posted.posted("SUPPLIER_BILL", id, "ISSUE"),
                row.attachmentKey(),
                row.notes(),
                lines,
                warning);
    }

    private long insertBill(
            SupplierBillForm form,
            String billNo,
            @Nullable String supplyState,
            Totals totals,
            BigDecimal total,
            @Nullable Long tdsSection,
            BigDecimal tdsRupees,
            BigDecimal payable,
            @Nullable String actor) {
        return jdbc.sql("""
                        INSERT INTO supplier_bill (party_id, bill_no, bill_date, due_date, supply_state_code,
                                                   taxable_rupees, tax_rupees, total_rupees, tds_section_id,
                                                   tds_rupees, payable_rupees, notes, created_by)
                        VALUES (:party, :billNo, :billDate, :dueDate, :state, :taxable, :tax, :total, :section,
                                :tds, :payable, :notes, :by)
                        RETURNING id
                        """)
                .param("party", form.partyId())
                .param("billNo", billNo)
                .param("billDate", form.billDate())
                .param("dueDate", form.dueDate())
                .param("state", supplyState)
                .param("taxable", totals.taxable())
                .param("tax", totals.tax())
                .param("total", total)
                .param("section", tdsSection)
                .param("tds", tdsRupees)
                .param("payable", payable)
                .param("notes", blankToNull(form.notes()))
                .param("by", actor)
                .query(Long.class)
                .single();
    }

    private List<Long> insertLines(long billId, List<LineCalc> lines) {
        var ids = new ArrayList<Long>(lines.size());
        int position = 1;
        for (var line : lines) {
            var form = line.form();
            ids.add(jdbc.sql("""
                            INSERT INTO supplier_bill_line (bill_id, position, kind, description, account_id,
                                                            stock_item_id, quantity, asset_class_id, hsn,
                                                            taxable_rupees, gst_rate_percent, cgst_rupees,
                                                            sgst_rupees, igst_rupees, total_rupees)
                            VALUES (:bill, :position, :kind, :description, :account, :stockItem, :quantity,
                                    :assetClass, :hsn, :taxable, :rate, :cgst, :sgst, :igst, :total)
                            RETURNING id
                            """)
                    .param("bill", billId)
                    .param("position", position++)
                    .param("kind", form.kind().name())
                    .param("description", form.description().trim())
                    .param("account", line.accountId())
                    .param("stockItem", form.stockItemId())
                    .param("quantity", form.quantity())
                    .param("assetClass", form.assetClassId())
                    .param("hsn", blankToNull(form.hsn()))
                    .param("taxable", line.taxable())
                    .param("rate", form.gstRatePercent())
                    .param("cgst", line.tax().cgst())
                    .param("sgst", line.tax().sgst())
                    .param("igst", line.tax().igst())
                    .param("total", line.tax().total())
                    .query(Long.class)
                    .single());
        }
        return ids;
    }

    /** Stock comes in and an asset joins the register, each line after its row exists. */
    private void applyStockAndAssets(
            LocalDate billDate,
            String billNo,
            List<LineCalc> lines,
            List<Long> lineIds,
            @Nullable String actor) {
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var form = line.form();
            if (form.kind() == SupplierBillLineForm.Kind.STOCK) {
                inventory.move(
                        form.stockItemId(),
                        new StockMovement.StockMovementForm(
                                StockMovement.Kind.IN, form.quantity(), null, "Bill " + billNo),
                        actor);
                inventory.setCost(form.stockItemId(), Rupees.divide(line.taxable(), form.quantity()));
            } else if (form.kind() == SupplierBillLineForm.Kind.ASSET) {
                jdbc.sql("""
                                INSERT INTO fixed_asset (name, asset_class_id, cost_rupees, put_to_use_on,
                                                         bill_line_id, created_by)
                                VALUES (:name, :class, :cost, :date, :line, :by)
                                """)
                        // The asset register's name holds 120 characters; a line description may be longer.
                        .param("name", truncate(form.description().trim(), 120))
                        .param("class", form.assetClassId())
                        .param("cost", line.taxable())
                        .param("date", billDate)
                        .param("line", lineIds.get(i))
                        .param("by", actor)
                        .update();
            }
        }
    }

    private List<LineCalc> prepareLines(List<SupplierBillLineForm> forms, boolean interState) {
        var lines = new ArrayList<LineCalc>(forms.size());
        for (var form : forms) {
            var accountId = switch (form.kind()) {
                case EXPENSE -> expenseAccount(form.accountId());
                case STOCK -> purchasesAccount(form.stockItemId(), form.quantity());
                case ASSET -> assetAccount(form.assetClassId());
            };
            var taxable = Rupees.of(form.taxableRupees());
            lines.add(new LineCalc(form, accountId, taxable, BillMath.tax(taxable, form.gstRatePercent(), interState)));
        }
        return lines;
    }

    private static Totals totals(List<LineCalc> lines) {
        var taxable = Rupees.sum(lines, LineCalc::taxable);
        var tax = Rupees.sum(
                lines, line -> line.tax().cgst().add(line.tax().sgst()).add(line.tax().igst()));
        return new Totals(taxable, tax);
    }

    private long expenseAccount(@Nullable Long accountId) {
        if (accountId == null
                || !jdbc.sql("SELECT EXISTS (SELECT 1 FROM account WHERE id = :id AND active AND type = 'EXPENSE')")
                        .param("id", accountId)
                        .query(Boolean.class)
                        .single()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick an expense account");
        }
        return accountId;
    }

    private long purchasesAccount(@Nullable Long stockItemId, @Nullable BigDecimal quantity) {
        if (stockItemId == null || quantity == null || quantity.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a stock item and quantity");
        }
        return jdbc.sql("SELECT id FROM account WHERE system_key = 'PURCHASES'")
                .query(Long.class)
                .single();
    }

    private long assetAccount(@Nullable Long assetClassId) {
        if (assetClassId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick an asset class");
        }
        return jdbc.sql("SELECT asset_account_id FROM asset_class WHERE id = :id AND active")
                .param("id", assetClassId)
                .query(AssetClassRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick an asset class"))
                .assetAccountId();
    }

    private @Nullable Long resolveTdsSection(
            @Nullable Long formSection,
            @Nullable Long partySection,
            BigDecimal tdsRupees,
            BigDecimal taxable) {
        if (tdsRupees.compareTo(taxable) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "TDS cannot be more than the bill's taxable value");
        }
        if (formSection != null) {
            boolean active = jdbc.sql("SELECT EXISTS (SELECT 1 FROM tds_section WHERE id = :id AND active)")
                    .param("id", formSection)
                    .query(Boolean.class)
                    .single();
            if (!active) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a TDS section");
            }
            return formSection;
        }
        if (tdsRupees.signum() > 0) {
            if (partySection == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a TDS section");
            }
            return partySection;
        }
        return null;
    }

    /** Takes back every STOCK line's quantity as it was before the bill changes. */
    private void reverseStock(long billId, String billNo, @Nullable String actor) {
        var oldLines = jdbc.sql("SELECT kind, stock_item_id, quantity FROM supplier_bill_line WHERE bill_id = :bill")
                .param("bill", billId)
                .query(OldLineRow.class)
                .list();
        for (var line : oldLines) {
            if ("STOCK".equals(line.kind()) && line.stockItemId() != null && line.quantity() != null) {
                inventory.move(
                        line.stockItemId(),
                        new StockMovement.StockMovementForm(
                                StockMovement.Kind.OUT, line.quantity().abs(), null, "Bill " + billNo),
                        actor);
            }
        }
    }

    /** Deletes the assets bought on a bill unless the books hold depreciation for one of them. */
    private void removeAssets(long billId, String blocked) {
        var assets = jdbc.sql("""
                        SELECT fa.id, fa.disposed_on
                        FROM fixed_asset fa
                        JOIN supplier_bill_line l ON l.id = fa.bill_line_id
                        WHERE l.bill_id = :bill
                        """)
                .param("bill", billId)
                .query(AssetRow.class)
                .list();
        for (var asset : assets) {
            if (asset.disposedOn() != null || depreciationPosted(asset.id())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, blocked);
            }
        }
        // Months scheduled but not yet posted go with the asset.
        jdbc.sql("""
                        DELETE FROM asset_depreciation
                        WHERE asset_id IN (SELECT fa.id FROM fixed_asset fa
                                           JOIN supplier_bill_line l ON l.id = fa.bill_line_id
                                           WHERE l.bill_id = :bill)
                        """)
                .param("bill", billId)
                .update();
        jdbc.sql("""
                        DELETE FROM fixed_asset
                        WHERE bill_line_id IN (SELECT id FROM supplier_bill_line WHERE bill_id = :bill)
                        """)
                .param("bill", billId)
                .update();
    }

    private boolean depreciationPosted(long assetId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1
                                       FROM asset_depreciation d
                                       JOIN journal_entry e ON e.source_type = 'ASSET_DEPRECIATION'
                                                           AND e.source_id = d.id
                                       WHERE d.asset_id = :id)
                        """)
                .param("id", assetId)
                .query(Boolean.class)
                .single();
    }

    /** A warning on the saved bill when the supplier's TDS threshold is crossed but no TDS was taken. */
    private @Nullable String warning(
            long id, long partyId, LocalDate billDate, BigDecimal taxable, BigDecimal tdsRupees) {
        if (tdsRupees.signum() > 0) {
            return null;
        }
        var suggestion = suggestTds(partyId, billDate, taxable, id);
        return suggestion.thresholdCrossed() ? suggestion.warning() : null;
    }

    private static @Nullable String supplyStateCode(@Nullable String typed, @Nullable String partyState) {
        var state = blankToNull(typed);
        if (state != null) {
            return state;
        }
        var name = blankToNull(partyState);
        return name == null ? null : IndianStates.code(name).orElse(null);
    }

    private static void checkDueDate(LocalDate billDate, @Nullable LocalDate dueDate) {
        if (dueDate != null && dueDate.isBefore(billDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due date is before the bill date");
        }
    }

    private static void checkPaidNow(SupplierBillForm form) {
        if (form.paidNow() != null) {
            // Task 4 replaces this with SupplierPaymentService.payBill.
            throw new UnsupportedOperationException("Paid now arrives with supplier payments");
        }
    }

    private static ResponseStatusException duplicate(String billNo, DuplicateKeyException cause) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "This supplier's bill " + billNo + " is already recorded", cause);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
