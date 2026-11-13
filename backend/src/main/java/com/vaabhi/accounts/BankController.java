package com.vaabhi.accounts;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Bank statement upload, preview and list, plus the actions on a line. Already OWNER-only in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin/accounts/bank")
class BankController {

    private final BankImportService imports;
    private final BankMatcher matcher;
    private final BankLineService lines;
    private final BankReconciliationService reconciliation;
    private final RazorpaySettlementQuery settlements;
    private final CodRemittanceService remittances;

    BankController(
            BankImportService imports,
            BankMatcher matcher,
            BankLineService lines,
            BankReconciliationService reconciliation,
            RazorpaySettlementQuery settlements,
            CodRemittanceService remittances) {
        this.imports = imports;
        this.matcher = matcher;
        this.lines = lines;
        this.reconciliation = reconciliation;
        this.settlements = settlements;
        this.remittances = remittances;
    }

    private static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    private static String fileName(MultipartFile file) {
        var name = file.getOriginalFilename();
        return name == null ? "statement" : name;
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the file");
        }
    }

    @PostMapping(value = "/statements/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FilePreview preview(@RequestPart("file") MultipartFile file) {
        try {
            return imports.preview(fileName(file), bytes(file));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(value = "/statements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    BankImportSummary importStatement(
            @RequestPart("file") MultipartFile file,
            @RequestPart("mapping") BankImportForm form,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        try {
            return imports.importStatement(
                    fileName(file),
                    bytes(file),
                    form.mapping(),
                    form.openingRupees(),
                    form.closingRupees(),
                    actor(jwt));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/statements")
    List<BankStatementSummary> listStatements() {
        return imports.list();
    }

    @GetMapping("/statements/{id}")
    BankStatementDetail getStatement(@PathVariable long id, @RequestParam(required = false) @Nullable String status) {
        return imports.detail(id, status);
    }

    @GetMapping("/statements/{id}/report")
    BankReconciliation getReport(@PathVariable long id) {
        return reconciliation.report(id);
    }

    @GetMapping(value = "/statements/{id}/report.csv", produces = "text/csv")
    ResponseEntity<String> reportCsv(@PathVariable long id) {
        var report = reconciliation.report(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"bank-reconciliation-"
                                + report.statement().periodTo() + ".csv\"")
                .body(reconciliation.csv(id));
    }

    // --- Razorpay settlements -----------------------------------------------------------------------------------

    @GetMapping("/razorpay/settlements")
    List<RazorpaySettlementView> listRazorpaySettlements() {
        return settlements.list();
    }

    // --- COD remittances ----------------------------------------------------------------------------------------

    @PostMapping(value = "/cod-remittances/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FilePreview previewRemittance(@RequestPart("file") MultipartFile file) {
        try {
            return remittances.preview(fileName(file), bytes(file));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(value = "/cod-remittances", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CodImportSummary importRemittance(
            @RequestPart("file") MultipartFile file,
            @RequestPart("mapping") ColumnMapping mapping,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        try {
            return remittances.importReport(fileName(file), bytes(file), mapping, actor(jwt));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/cod-remittances")
    List<CodRemittanceView> listRemittances() {
        return remittances.list();
    }

    // --- line actions -------------------------------------------------------------------------------------------

    @GetMapping("/lines/{id}/candidates")
    List<MatchCandidate> candidates(@PathVariable long id) {
        return matcher.candidates(id);
    }

    @PostMapping("/lines/{id}/match")
    BankLineView match(
            @PathVariable long id, @Valid @RequestBody BankMatchForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return lines.match(id, form, actor(jwt));
    }

    @PostMapping("/lines/{id}/post")
    BankLineView post(
            @PathVariable long id, @Valid @RequestBody BankPostForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return lines.post(id, form, actor(jwt));
    }

    @PostMapping("/lines/{id}/ignore")
    BankLineView ignore(
            @PathVariable long id,
            @Valid @RequestBody BankIgnoreForm form,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        return lines.ignore(id, form, actor(jwt));
    }

    @PostMapping("/lines/{id}/undo")
    BankLineView undo(@PathVariable long id, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return lines.undo(id, actor(jwt));
    }
}
