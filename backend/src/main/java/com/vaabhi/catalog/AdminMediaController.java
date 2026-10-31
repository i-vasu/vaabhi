package com.vaabhi.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Product photos for the admin app (ADMIN role). Uploads go straight from the browser to R2. */
@RestController
@RequestMapping("/api/v1/admin/products/{productId}/media")
class AdminMediaController {

    private final ProductMediaService service;

    AdminMediaController(ProductMediaService service) {
        this.service = service;
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    MediaUpload startMediaUpload(@PathVariable long productId, @Valid @RequestBody MediaUploadRequest request) {
        return service.startUpload(productId, request);
    }

    @PostMapping("/{mediaId}/complete")
    List<AdminMedia> completeMediaUpload(@PathVariable long productId, @PathVariable long mediaId) {
        return service.completeUpload(productId, mediaId);
    }

    @PutMapping
    List<AdminMedia> updateMedia(
            @PathVariable long productId,
            @RequestBody @NotNull @Size(max = ProductMediaService.MAX_PHOTOS)
                    List<@Valid @NotNull MediaUpdate> updates) {
        return service.update(productId, updates);
    }

    @DeleteMapping("/{mediaId}")
    List<AdminMedia> deleteMedia(@PathVariable long productId, @PathVariable long mediaId) {
        return service.delete(productId, mediaId);
    }
}
