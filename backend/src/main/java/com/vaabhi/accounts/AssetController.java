package com.vaabhi.accounts;

import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The fixed-asset register. Reads need MONEY; writes the owner (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/accounts/assets")
class AssetController {

    private final AssetService assets;

    AssetController(AssetService assets) {
        this.assets = assets;
    }

    private static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    @GetMapping("/classes")
    List<AssetClassView> classes() {
        return assets.classes();
    }

    @PostMapping("/classes")
    @ResponseStatus(HttpStatus.CREATED)
    AssetClassView createClass(@Valid @RequestBody AssetClassForm form) {
        return assets.createClass(form);
    }

    @PutMapping("/classes/{id}")
    AssetClassView updateClass(@PathVariable long id, @Valid @RequestBody AssetClassForm form) {
        return assets.updateClass(id, form);
    }

    @GetMapping
    List<FixedAssetView> register() {
        return assets.register();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    FixedAssetView createOpening(
            @Valid @RequestBody OpeningAssetForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return assets.createOpening(form, actor(jwt));
    }

    @PutMapping("/{id}")
    FixedAssetView update(@PathVariable long id, @Valid @RequestBody AssetUpdateForm form) {
        return assets.update(id, form);
    }

    @PostMapping("/{id}/dispose")
    FixedAssetView dispose(@PathVariable long id, @Valid @RequestBody AssetDisposalForm form) {
        return assets.dispose(id, form);
    }
}
