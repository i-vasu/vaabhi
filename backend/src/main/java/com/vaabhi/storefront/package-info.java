/**
 * Storefront content: home-page banners (hero slides, occasion tiles, the craft section) and category photos,
 * uploaded from the dashboard to R2 and served to the storefront by {@code GET /api/v1/store/home} and the
 * category list. Uses the catalog module for categories; nothing depends on this module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Storefront")
package com.vaabhi.storefront;
