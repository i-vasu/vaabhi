package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Alterations. Requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/alterations")
class AlterationController {

    private final AlterationService alterations;
    private final TailoringImage images;

    AlterationController(AlterationService alterations, TailoringImage images) {
        this.alterations = alterations;
        this.images = images;
    }

    @GetMapping
    AlterationPage listAlterations(
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(defaultValue = "OPEN") OrderService.Filter filter,
            @RequestParam(required = false) @Nullable Long clientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return alterations.list(q, filter, clientId, ClientController.page(page), ClientController.pageSize(pageSize));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Alteration createAlteration(@Valid @RequestBody AlterationForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        form.items().forEach(item -> images.requireUploaded(TailoringImage.Purpose.ALTERATION, item.photoKey()));
        return alterations.create(form, ClientController.actor(jwt));
    }

    @GetMapping("/{id}")
    Alteration getAlteration(@PathVariable long id) {
        return alterations.get(id);
    }

    @PostMapping("/{id}/progress")
    Alteration progressAlteration(
            @PathVariable long id,
            @Valid @RequestBody AlterationProgress progress,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        return alterations.progress(id, progress, ClientController.actor(jwt));
    }
}
