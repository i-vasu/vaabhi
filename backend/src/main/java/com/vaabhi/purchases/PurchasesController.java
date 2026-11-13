package com.vaabhi.purchases;

import com.vaabhi.accounts.ColumnMapping;
import com.vaabhi.accounts.FilePreview;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Suppliers, their bills, payments and the bill attachments. Requires the MONEY area (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/money/purchases")
class PurchasesController {

    private final SupplierService suppliers;
    private final SupplierBillService bills;
    private final SupplierPaymentService payments;
    private final ShiprocketInvoiceImport shiprocket;

    PurchasesController(
            SupplierService suppliers,
            SupplierBillService bills,
            SupplierPaymentService payments,
            ShiprocketInvoiceImport shiprocket) {
        this.suppliers = suppliers;
        this.bills = bills;
        this.payments = payments;
        this.shiprocket = shiprocket;
    }

    private static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    private static @Nullable YearMonth parseMonth(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The month must look like 2026-11", e);
        }
    }

    @GetMapping("/suppliers")
    List<SupplierBalance> listSuppliers(@RequestParam(required = false) @Nullable String q) {
        return suppliers.list(q);
    }

    @GetMapping("/suppliers/{id}")
    SupplierStatement getSupplier(@PathVariable long id) {
        return suppliers.statement(id);
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    SupplierCreated createSupplier(@Valid @RequestBody SupplierForm form) {
        return new SupplierCreated(suppliers.create(form));
    }

    @PutMapping("/suppliers/{id}")
    SupplierStatement updateSupplier(@PathVariable long id, @Valid @RequestBody SupplierForm form) {
        suppliers.update(id, form);
        return suppliers.statement(id);
    }

    @GetMapping("/bills")
    List<SupplierBillListEntry> listBills(
            @RequestParam(required = false) @Nullable Long partyId,
            @RequestParam(required = false) @Nullable String status,
            @RequestParam(required = false) @Nullable String month,
            @RequestParam(required = false) @Nullable String source) {
        return bills.list(partyId, status, parseMonth(month), source);
    }

    @GetMapping("/bills/{id}")
    SupplierBillView getBill(@PathVariable long id) {
        return bills.get(id);
    }

    @PostMapping("/bills")
    @ResponseStatus(HttpStatus.CREATED)
    SupplierBillView createBill(@Valid @RequestBody SupplierBillForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return bills.create(form, actor(jwt));
    }

    @PutMapping("/bills/{id}")
    SupplierBillView updateBill(
            @PathVariable long id,
            @Valid @RequestBody SupplierBillForm form,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        return bills.update(id, form, actor(jwt));
    }

    @PostMapping("/bills/{id}/cancel")
    SupplierBillView cancelBill(
            @PathVariable long id,
            @Valid @RequestBody BillCancelForm form,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        return bills.cancel(id, form.on(), form.reason(), actor(jwt));
    }

    @PostMapping("/bills/{id}/attachment")
    AttachmentUpload startAttachment(@PathVariable long id, @RequestBody AttachmentStartForm form) {
        return bills.startAttachment(id, form.contentType());
    }

    @PutMapping("/bills/{id}/attachment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmAttachment(@PathVariable long id, @RequestBody AttachmentConfirmForm form) {
        bills.confirmAttachment(id, form.key());
    }

    @PostMapping("/bills/tds-check")
    TdsSuggestion checkTds(@Valid @RequestBody TdsCheckForm form) {
        return bills.suggestTds(form.partyId(), form.billDate(), form.taxableRupees(), form.billId());
    }

    @GetMapping("/payments")
    List<SupplierPaymentView> listPayments(@RequestParam(required = false) @Nullable Long partyId) {
        return payments.list(partyId);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    SupplierPaymentView createPayment(
            @Valid @RequestBody SupplierPaymentForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return payments.create(form, actor(jwt));
    }

    @DeleteMapping("/payments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deletePayment(@PathVariable long id) {
        payments.delete(id);
    }

    @PostMapping("/payments/from-bank-line")
    @ResponseStatus(HttpStatus.CREATED)
    SupplierPaymentView payFromBankLine(
            @Valid @RequestBody BankLinePaymentForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return payments.fromBankLine(form, actor(jwt));
    }

    @PostMapping(value = "/shiprocket-invoices/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FilePreview previewShiprocketInvoices(@RequestPart("file") MultipartFile file) {
        try {
            return shiprocket.preview(file);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(value = "/shiprocket-invoices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ShiprocketInvoiceSummary importShiprocketInvoices(
            @RequestPart("file") MultipartFile file,
            @RequestPart("mapping") ColumnMapping mapping,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        try {
            return shiprocket.importFile(file, mapping, actor(jwt));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
