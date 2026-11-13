package com.vaabhi.accounts;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final RazorpaySettlementQuery settlements;

    BankController(
            BankImportService imports,
            BankMatcher matcher,
            BankLineService lines,
            RazorpaySettlementQuery settlements) {
        this.imports = imports;
        this.matcher = matcher;
        this.lines = lines;
        this.settlements = settlements;
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

    // --- Razorpay settlements -----------------------------------------------------------------------------------

    @GetMapping("/razorpay/settlements")
    List<RazorpaySettlementView> listRazorpaySettlements() {
        return settlements.list();
    }

    // --- line actions -------------------------------------------------------------------------------------------

    @GetMapping("/lines/{id}/candidates")
    List<MatchCandidate> candidates(@PathVariable long id) {
        return matcher.candidates(id);
    }

    @PostMapping("/lines/{id}/match")
    BankLineView match(
            @PathVariable long id,
            @Valid @RequestBody BankMatchForm form,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        return lines.match(id, form, actor(jwt));
    }

    @PostMapping("/lines/{id}/post")
    BankLineView post(
            @PathVariable long id,
            @Valid @RequestBody BankPostForm form,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
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
