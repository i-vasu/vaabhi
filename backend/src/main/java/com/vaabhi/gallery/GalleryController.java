package com.vaabhi.gallery;

import jakarta.validation.Valid;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
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

/** Design and customer galleries. Needs the MARKETING permission (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/gallery")
class GalleryController {

    private final GalleryService gallery;

    GalleryController(GalleryService gallery) {
        this.gallery = gallery;
    }

    @GetMapping
    List<GalleryPhoto> listGallery(
            @RequestParam(required = false) GalleryPhoto.@Nullable Kind kind,
            @RequestParam(required = false) @Nullable Long templateId,
            @RequestParam(required = false) @Nullable Long clientId,
            @RequestParam(required = false) @Nullable String q) {
        return gallery.list(kind, templateId, clientId, q);
    }

    @PostMapping("/uploads")
    GalleryPhoto.GalleryUpload startGalleryUpload() {
        return gallery.startUpload();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GalleryPhoto addGalleryPhoto(
            @Valid @RequestBody GalleryPhoto.GalleryForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return gallery.save(null, form, jwt == null ? null : jwt.getClaimAsString("name"));
    }

    @PutMapping("/{id}")
    GalleryPhoto updateGalleryPhoto(@PathVariable long id, @Valid @RequestBody GalleryPhoto.GalleryForm form) {
        return gallery.save(id, form, null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGalleryPhoto(@PathVariable long id) {
        gallery.remove(id);
    }
}
