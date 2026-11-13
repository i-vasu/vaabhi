package com.vaabhi.accounts;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/** The chart, journal, trial balance, periods and books settings. Reads need MONEY; writes the owner (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/accounts")
class AccountsController {

    private static final Logger log = LoggerFactory.getLogger(AccountsController.class);
    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    private final ChartService chart;
    private final JournalService journal;
    private final PeriodService periods;
    private final OpeningService opening;
    private final BooksSettingsService settings;
    private final PostingProblemService problems;
    private final AccountsStatusService status;
    private final JournalSweeper sweeper;
    private final DailyBooksCheck booksCheck;
    private final InputGstReport inputGst;
    private final Clock clock;

    AccountsController(
            ChartService chart,
            JournalService journal,
            PeriodService periods,
            OpeningService opening,
            BooksSettingsService settings,
            PostingProblemService problems,
            AccountsStatusService status,
            JournalSweeper sweeper,
            DailyBooksCheck booksCheck,
            InputGstReport inputGst,
            Clock clock) {
        this.chart = chart;
        this.journal = journal;
        this.periods = periods;
        this.opening = opening;
        this.settings = settings;
        this.problems = problems;
        this.status = status;
        this.sweeper = sweeper;
        this.booksCheck = booksCheck;
        this.inputGst = inputGst;
        this.clock = clock;
    }

    private static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SHOP_ZONE));
    }

    private static BigDecimal orZero(@Nullable BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static YearMonth month(String text) {
        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a month like 2026-11");
        }
    }

    // --- chart --------------------------------------------------------------------------------------------------

    @GetMapping("/accounts")
    List<Account> listAccounts() {
        return chart.list();
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    Account createAccount(@Valid @RequestBody AccountForm form) {
        return chart.create(form);
    }

    @PutMapping("/accounts/{id}")
    Account updateAccount(@PathVariable long id, @Valid @RequestBody AccountForm form) {
        return chart.update(id, form);
    }

    @GetMapping("/accounts/{id}/ledger")
    LedgerStatement ledger(
            @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to) {
        return journal.ledger(id, from != null ? from : today().withDayOfMonth(1), to != null ? to : today());
    }

    // --- journal ------------------------------------------------------------------------------------------------

    @GetMapping("/journal")
    JournalPage listJournal(
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return journal.list(q, from, to, Math.max(1, page), Math.clamp(size, 1, 100));
    }

    @GetMapping("/journal/{id}")
    JournalEntry getJournalEntry(@PathVariable long id) {
        return journal.get(id);
    }

    @PostMapping("/journal")
    @ResponseStatus(HttpStatus.CREATED)
    JournalEntry postManual(@Valid @RequestBody ManualEntryForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        var lines = form.lines().stream()
                .map(line -> new NewEntryLine(
                        line.accountId(),
                        line.partyId(),
                        orZero(line.debitRupees()),
                        orZero(line.creditRupees()),
                        line.memo()))
                .toList();
        return journal.post(
                new NewEntry(form.date(), "MANUAL", null, "MANUAL", form.narration(), null, null, lines), actor(jwt));
    }

    @PostMapping("/journal/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    JournalEntry reverse(@PathVariable long id, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return journal.reverse(id, actor(jwt));
    }

    // --- reports ------------------------------------------------------------------------------------------------

    @GetMapping("/trial-balance")
    TrialBalance trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate asOf) {
        return journal.trialBalance(asOf != null ? asOf : today());
    }

    @GetMapping("/input-gst")
    List<InputGstMonth> inputGst(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return inputGst.months(from, to);
    }

    @GetMapping(value = "/input-gst.csv", produces = "text/csv")
    ResponseEntity<String> inputGstCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"input-gst-" + from + "-" + to + ".csv\"")
                .body(inputGst.csv(from, to));
    }

    // --- opening balances ---------------------------------------------------------------------------------------

    @GetMapping("/opening-balances")
    OpeningBalances getOpeningBalances() {
        return opening.get();
    }

    @PutMapping("/opening-balances")
    OpeningBalances saveOpeningBalances(
            @Valid @RequestBody OpeningBalancesForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        var lines = form.lines().stream()
                .map(line -> new OpeningBalanceLine(
                        line.accountId(), orZero(line.debitRupees()), orZero(line.creditRupees())))
                .toList();
        return opening.save(lines, actor(jwt));
    }

    // --- periods ------------------------------------------------------------------------------------------------

    @GetMapping("/periods")
    List<PeriodStatus> listPeriods() {
        return periods.list();
    }

    @PutMapping("/periods/{month}/lock")
    List<PeriodStatus> lockPeriod(@PathVariable String month, @AuthenticationPrincipal @Nullable Jwt jwt) {
        periods.lock(month(month), actor(jwt));
        return periods.list();
    }

    @DeleteMapping("/periods/{month}/lock")
    List<PeriodStatus> unlockPeriod(@PathVariable String month) {
        periods.unlock(month(month));
        return periods.list();
    }

    // --- status, sweep, problems, settings ----------------------------------------------------------------------

    @GetMapping("/status")
    AccountsStatus getStatus() {
        return status.get();
    }

    @PostMapping("/sweep")
    JournalSweeper.SweepResult sweep() {
        var result = sweeper.sweepNow();
        // Post now also refreshes the balance checks, so the problems list is current.
        try {
            booksCheck.run();
        } catch (RuntimeException e) {
            log.error("Balance checks after Post now failed", e);
        }
        return result;
    }

    @GetMapping("/problems")
    List<PostingProblem> listProblems() {
        return problems.list();
    }

    @GetMapping("/settings")
    BooksSettings getSettings() {
        return settings.get();
    }

    @PutMapping("/settings")
    BooksSettings saveSettings(@Valid @RequestBody BooksSettingsForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return settings.save(
                new BooksSettings(form.startDate(), form.modeAccounts(), null, form.shiprocketPartyId()), actor(jwt));
    }
}
