package com.vaabhi.accounts;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Schedule III profit and loss account and balance sheet. Reads need MONEY (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/accounts/statements")
class StatementsController {

    private final StatementService statements;

    StatementsController(StatementService statements) {
        this.statements = statements;
    }

    @GetMapping("/profit-and-loss")
    ProfitAndLoss profitAndLoss(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statements.profitAndLoss(from, to);
    }

    @GetMapping("/balance-sheet")
    BalanceSheet balanceSheet(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return statements.balanceSheet(asOf);
    }
}
