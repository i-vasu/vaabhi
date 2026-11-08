package com.vaabhi.storefront;

import com.vaabhi.settings.SettingsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Content pages and the shop's contact details. {@code /api/v1/admin/storefront/**} needs the CATALOGUE
 * permission; {@code /api/v1/store/**} is public (see SecurityConfig).
 */
@RestController
class ContentPageController {

    private final ContentPageService pages;
    private final SettingsService settings;

    ContentPageController(ContentPageService pages, SettingsService settings) {
        this.pages = pages;
        this.settings = settings;
    }

    /** Published pages, for the storefront footer. */
    @GetMapping("/api/v1/store/pages")
    List<ContentPage.ContentPageLink> listPublishedPages() {
        return pages.published();
    }

    @GetMapping("/api/v1/store/pages/{slug}")
    ContentPage getPublishedPage(@PathVariable String slug) {
        return pages.publishedPage(slug);
    }

    /** Phone, WhatsApp, email, address, socials and hours from the shop profile. */
    @GetMapping("/api/v1/store/shop")
    ShopContact getShopContact() {
        return ShopContact.of(settings.shopProfile());
    }

    @GetMapping("/api/v1/admin/storefront/pages")
    List<ContentPage> listPages() {
        return pages.list();
    }

    @PostMapping("/api/v1/admin/storefront/pages")
    @ResponseStatus(HttpStatus.CREATED)
    ContentPage createPage(@Valid @RequestBody ContentPage.ContentPageForm form) {
        return pages.save(null, form);
    }

    @PutMapping("/api/v1/admin/storefront/pages/{id}")
    ContentPage updatePage(@PathVariable long id, @Valid @RequestBody ContentPage.ContentPageForm form) {
        return pages.save(id, form);
    }

    @DeleteMapping("/api/v1/admin/storefront/pages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deletePage(@PathVariable long id) {
        pages.remove(id);
    }
}
