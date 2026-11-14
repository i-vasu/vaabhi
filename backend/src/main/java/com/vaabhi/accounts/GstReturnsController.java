package com.vaabhi.accounts;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** GSTR-3B: the month summary and its CSV download. Reads need MONEY (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/accounts")
class GstReturnsController {

    private final Gstr3bService gstr3b;

    GstReturnsController(Gstr3bService gstr3b) {
        this.gstr3b = gstr3b;
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must look like 2026-05");
        }
    }

    @GetMapping("/gstr3b")
    Gstr3b getGstr3b(@RequestParam String month) {
        return gstr3b.month(parseMonth(month));
    }

    @GetMapping("/gstr3b.csv")
    ResponseEntity<String> getGstr3bCsv(@RequestParam String month) {
        var yearMonth = parseMonth(month);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gstr3b-" + yearMonth + ".csv\"")
                .body(gstr3b.csv(yearMonth));
    }
}
