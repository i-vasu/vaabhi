package com.vaabhi.accounts;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Bank statement upload, preview and list. Already OWNER-only in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin/accounts/bank")
class BankController {

    private final BankImportService imports;

    BankController(BankImportService imports) {
        this.imports = imports;
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
}
