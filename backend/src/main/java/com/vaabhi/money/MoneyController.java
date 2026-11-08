package com.vaabhi.money;

import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

/** Invoices, expenses, the ledger and what customers owe. Requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/money")
class MoneyController {

    private final InvoiceService invoices;
    private final ExpenseService expenses;
    private final LedgerService ledger;
    private final Clock clock;

    MoneyController(InvoiceService invoices, ExpenseService expenses, LedgerService ledger, Clock clock) {
        this.invoices = invoices;
        this.expenses = expenses;
        this.ledger = ledger;
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
