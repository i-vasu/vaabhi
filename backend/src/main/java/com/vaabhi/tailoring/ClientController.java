package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import java.util.Objects;
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

/** Shop customers, their measurements, and counter photo uploads. Requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
class ClientController {

    static final int MAX_PAGE_SIZE = 100;

    private final ClientService clients;
    private final TailoringImage images;

    ClientController(ClientService clients, TailoringImage images) {
        this.clients = clients;
        this.images = images;
    }

    /** The staff member's name from the access token, for "taken by" / "booked by". */
    static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    static int page(int page) {
        return Math.max(1, page);
    }

    static int pageSize(int pageSize) {
        return Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
    }

    @GetMapping("/clients")
    ClientPage listClients(
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(defaultValue = "RECENT") ClientService.Sort sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return clients.list(q, sort, page(page), pageSize(pageSize));
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    Client createClient(@Valid @RequestBody ClientForm form) {
        images.requireUploaded(TailoringImage.Purpose.CLIENT, form.photoKey());
        return clients.create(form);
    }

    @GetMapping("/clients/{id}")
    Client getClient(@PathVariable long id) {
        return clients.get(id);
    }

    @PutMapping("/clients/{id}")
    Client updateClient(@PathVariable long id, @Valid @RequestBody ClientForm form) {
        if (!Objects.equals(form.photoKey(), clients.get(id).photoKey())) {
            images.requireUploaded(TailoringImage.Purpose.CLIENT, form.photoKey());
        }
        return clients.update(id, form);
    }

    @PostMapping("/clients/{id}/measurements")
    @ResponseStatus(HttpStatus.CREATED)
    Measurement addMeasurement(
            @PathVariable long id,
            @Valid @RequestBody MeasurementForm form,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        return clients.addMeasurement(id, form, actor(jwt));
    }

    @PostMapping("/tailoring/images")
    TailoringImageUpload startTailoringImageUpload(@Valid @RequestBody TailoringImageRequest request) {
        return images.start(request.purpose());
    }
}
