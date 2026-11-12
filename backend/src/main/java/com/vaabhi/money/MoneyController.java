package com.vaabhi.money;

import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Invoices, expenses, the ledger and what customers owe. Requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/money")
class MoneyController {

    private final InvoiceService invoices;
    private final ExpenseService expenses;
    private final LedgerService ledger;
    private final TaxClassService taxClasses;
    private final CreditNoteService creditNotes;
    private final Gstr1Service gstr1;
    private final Clock clock;

    MoneyController(
            InvoiceService invoices,
            ExpenseService expenses,
            LedgerService ledger,
            TaxClassService taxClasses,
            CreditNoteService creditNotes,
            Gstr1Service gstr1,
            Clock clock) {
        this.creditNotes = creditNotes;
        this.gstr1 = gstr1;
        this.invoices = invoices;
        this.expenses = expenses;
        this.ledger = ledger;
        this.taxClasses = taxClasses;
        this.clock = clock;
    }

    private static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    /** This month so far when no range is given. */
    private LocalDate from(@Nullable LocalDate from) {
        return from != null ? from : LocalDate.now(clock).withDayOfMonth(1);
    }

    private LocalDate to(@Nullable LocalDate to) {
        return to != null ? to : LocalDate.now(clock);
    }

    // --- invoices -----------------------------------------------------------------------------------------------

    @GetMapping("/invoices")
    InvoicePage listInvoices(
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return invoices.list(q, from, to, Math.max(1, page), Math.clamp(pageSize, 1, 100));
    }

    @GetMapping("/invoices/{id}")
    Invoice getInvoice(@PathVariable long id) {
        return invoices.get(id);
    }

    @PostMapping("/invoices/{id}/cancel")
    Invoice cancelInvoice(@PathVariable long id, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return invoices.cancel(id, actor(jwt));
    }

    // --- credit notes -------------------------------------------------------------------------------------------

    @PostMapping("/invoices/{id}/credit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    CreditNote issueCreditNote(
            @PathVariable long id,
            @Valid @RequestBody CreditNoteForm form,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        return creditNotes.issue(id, form, actor(jwt));
    }

    @GetMapping("/invoices/{id}/credit-notes")
    List<CreditNoteSummary> listInvoiceCreditNotes(@PathVariable long id) {
        return creditNotes.forInvoice(id);
    }

    @GetMapping("/credit-notes")
    CreditNotePage listCreditNotes(
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return creditNotes.list(q, Math.max(1, page), Math.clamp(size, 1, 100));
    }

    @GetMapping("/credit-notes/{id}")
    CreditNote getCreditNote(@PathVariable long id) {
        return creditNotes.get(id);
    }

    /** The order's live invoice; 204 when it has none yet. */
    @GetMapping("/orders/{orderId}/invoice")
    ResponseEntity<Invoice> getOrderInvoice(@PathVariable long orderId) {
        return invoices.forOrder(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/orders/{orderId}/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    Invoice issueOrderInvoice(@PathVariable long orderId, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return invoices.issue(orderId, actor(jwt));
    }

    // --- tax classes --------------------------------------------------------------------------------------------

    @GetMapping("/tax-classes")
    List<TaxClass> listTaxClasses() {
        return taxClasses.list();
    }

    @PostMapping("/tax-classes")
    @ResponseStatus(HttpStatus.CREATED)
    TaxClass createTaxClass(@Valid @RequestBody TaxClassForm form) {
        return taxClasses.create(form);
    }

    @PutMapping("/tax-classes/{id}")
    TaxClass updateTaxClass(@PathVariable long id, @Valid @RequestBody TaxClassForm form) {
        return taxClasses.update(id, form);
    }

    @DeleteMapping("/tax-classes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTaxClass(@PathVariable long id) {
        taxClasses.delete(id);
    }

    // --- GSTR-1 -------------------------------------------------------------------------------------------------

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The month must look like 2026-11");
        }
    }

    @GetMapping("/gstr1")
    Gstr1 getGstr1(@RequestParam String month) {
        return gstr1.month(parseMonth(month));
    }

    @GetMapping("/gstr1/{section}.csv")
    ResponseEntity<String> getGstr1Csv(@PathVariable String section, @RequestParam String month) {
        var yearMonth = parseMonth(month);
        var csv = gstr1.csv(yearMonth, section);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"gstr1-" + section.toLowerCase(Locale.ROOT) + "-" + yearMonth + ".csv\"")
                .body(csv);
    }

    // --- expenses -----------------------------------------------------------------------------------------------

    @GetMapping("/expenses")
    ExpenseList listExpenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
            @RequestParam(required = false) @Nullable ExpenseCategory category) {
        return expenses.list(from(from), to(to), category);
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    Expense createExpense(@Valid @RequestBody ExpenseForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return expenses.create(form, actor(jwt));
    }

    @PutMapping("/expenses/{id}")
    Expense updateExpense(@PathVariable long id, @Valid @RequestBody ExpenseForm form) {
        return expenses.update(id, form);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteExpense(@PathVariable long id) {
        expenses.delete(id);
    }

    // --- ledger and dues ----------------------------------------------------------------------------------------

    @GetMapping("/ledger")
    Ledger getLedger(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
            @RequestParam(required = false) LedgerEntry.@Nullable Kind kind) {
        return ledger.ledger(from(from), to(to), kind);
    }

    @GetMapping("/outstanding")
    Outstanding getOutstanding(@RequestParam(required = false) @Nullable Long clientId) {
        return ledger.outstanding(clientId);
    }
}
