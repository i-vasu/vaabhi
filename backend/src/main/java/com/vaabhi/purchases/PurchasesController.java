package com.vaabhi.purchases;

import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Suppliers, their bills and the bill attachments. Requires the MONEY area (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/money/purchases")
class PurchasesController {

    private final SupplierService suppliers;
    private final SupplierBillService bills;

    PurchasesController(SupplierService suppliers, SupplierBillService bills) {
        this.suppliers = suppliers;
        this.bills = bills;
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
}
