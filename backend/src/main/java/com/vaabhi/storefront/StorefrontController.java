package com.vaabhi.storefront;

import com.vaabhi.catalog.CategoryResponse;
import com.vaabhi.catalog.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Storefront content. {@code /api/v1/admin/storefront/**} needs the CATALOGUE permission; {@code /api/v1/store/home}
 * is public (see SecurityConfig).
 */
@RestController
class StorefrontController {

    private final StorefrontService storefront;
    private final CategoryService categories;
    private final InstagramReels instagram;

    StorefrontController(StorefrontService storefront, CategoryService categories, InstagramReels instagram) {
        this.storefront = storefront;
        this.categories = categories;
        this.instagram = instagram;
    }

    @GetMapping("/api/v1/store/home")
    Banner.HomeContent getHomeContent() {
        return storefront.home();
    }

    /** The latest reels from the shop's Instagram, newest first; empty until the Instagram token is set. */
    @GetMapping("/api/v1/store/instagram")
    List<InstagramReel> listInstagramReels() {
        return instagram.latest();
    }

    @GetMapping("/api/v1/admin/storefront/banners")
    List<Banner> listBanners(@RequestParam(required = false) Banner.@Nullable Slot slot) {
        return storefront.list(slot);
    }

    @PostMapping("/api/v1/admin/storefront/banners")
    @ResponseStatus(HttpStatus.CREATED)
    Banner createBanner(@Valid @RequestBody Banner.BannerForm form) {
        return storefront.save(null, form);
    }

    @PutMapping("/api/v1/admin/storefront/banners/{id}")
    Banner updateBanner(@PathVariable long id, @Valid @RequestBody Banner.BannerForm form) {
        return storefront.save(id, form);
    }

    @DeleteMapping("/api/v1/admin/storefront/banners/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteBanner(@PathVariable long id) {
        storefront.remove(id);
    }

    @PutMapping("/api/v1/admin/storefront/banners/order")
    List<Banner> reorderBanners(@Valid @RequestBody Banner.BannerOrder order) {
        return storefront.reorder(order);
    }

    @PostMapping("/api/v1/admin/storefront/uploads")
    Banner.SiteImageUpload startSiteImageUpload() {
        return storefront.startUpload();
    }

    @GetMapping("/api/v1/admin/storefront/categories")
    List<CategoryResponse> listStorefrontCategories() {
        return categories.listCategories();
    }

    @PutMapping("/api/v1/admin/storefront/categories/{id}/image")
    CategoryResponse setCategoryImage(@PathVariable long id, @Valid @RequestBody Banner.CategoryImageForm form) {
        return storefront.setCategoryImage(id, form.imageKey());
    }
}
