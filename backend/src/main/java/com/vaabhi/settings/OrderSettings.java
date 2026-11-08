package com.vaabhi.settings;

/**
 * Switches for orders and the product page.
 *
 * @param trialDate adds a trial / fitting date to new orders and shows it on the order and in the workshop
 * @param subCategories adds a sub-category field to products
 * @param barcodes shows the barcode field on a product
 * @param lazyProductList do not preload products on a new order (for very large catalogues)
 * @param tailorChargeOnAddons record what the shop pays the worker for each add-on (never printed)
 */
public record OrderSettings(
        boolean trialDate,
        boolean subCategories,
        boolean barcodes,
        boolean lazyProductList,
        boolean tailorChargeOnAddons) {

    static OrderSettings defaults() {
        return new OrderSettings(false, false, false, false, false);
    }
}
