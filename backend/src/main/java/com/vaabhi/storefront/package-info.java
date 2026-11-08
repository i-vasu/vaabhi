/**
 * Storefront content: home-page banners (hero slides, occasion tiles, the craft section), category photos and
 * content pages (policies, guides, about), plus the shop's public contact details. Photos are
 * uploaded from the dashboard to R2 and served to the storefront by {@code GET /api/v1/store/home} and the
 * category list. Uses the catalog module for categories and settings for the shop profile; nothing depends on
 * this module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Storefront")
package com.vaabhi.storefront;
