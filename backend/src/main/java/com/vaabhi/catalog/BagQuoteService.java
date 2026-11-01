package com.vaabhi.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prices a bag from the database. The browser keeps the bag, but prices, add-ons and stock always come from here,
 * so a changed price or a sold-out item is shown before checkout.
 */
@Service
public class BagQuoteService {

    /** Per line, as agreed with the shop. */
    public static final int MAX_QUANTITY = 3;

    private final JdbcClient jdbc;

    BagQuoteService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record VariantRow(
            long productId,
            String sku,
            String slug,
            String name,
            String colourName,
            String colourHex,
            @Nullable String size,
            int pricePaise,
            int mrpPaise,
            int stockQty) {}

    private record AddonRow(long productId, String code, String name, int pricePaise, String input) {}

    private record ImageRow(long productId, @Nullable String colourName, String storageKey) {}

    @Transactional(readOnly = true)
    public BagQuote quote(BagQuoteRequest request) {
        var skus = request.lines().stream()
                .map(BagQuoteRequest.Line::sku)
                .distinct()
                .toList();
        if (skus.isEmpty()) {
            return new BagQuote(List.of(), 0, 0, 0, 0);
        }
        Map<String, VariantRow> variants = new HashMap<>();
        jdbc.sql("""
                        SELECT p.id AS product_id, v.sku, p.slug, p.name, v.colour_name, v.colour_hex, v.size,
                               v.price_paise, v.mrp_paise, v.stock_qty
                        FROM product_variant v JOIN product p ON p.id = v.product_id
                        WHERE v.sku IN (:skus) AND v.active AND p.status = 'PUBLISHED'
                        """).param("skus", skus).query(VariantRow.class).list().forEach(v -> variants.put(v.sku(), v));

        var productIds =
                variants.values().stream().map(VariantRow::productId).distinct().toList();
        Map<String, AddonRow> addons = new HashMap<>();
        List<ImageRow> images = List.of();
        if (!productIds.isEmpty()) {
            jdbc.sql("""
                            SELECT product_id, code, name, price_paise, input FROM product_addon
                            WHERE product_id IN (:ids) AND active
                            """)
                    .param("ids", productIds)
                    .query(AddonRow.class)
                    .list()
                    .forEach(a -> addons.put(a.productId() + "|" + a.code(), a));
            images =
                    jdbc.sql("""
                            SELECT product_id, colour_name, storage_key FROM product_media
                            WHERE product_id IN (:ids) AND kind = 'IMAGE' AND status = 'READY'
                            ORDER BY product_id, position, id
                            """).param("ids", productIds).query(ImageRow.class).list();
        }

        var lines = new ArrayList<BagQuote.Line>();
        int items = 0;
        int addonTotal = 0;
        int mrp = 0;
        for (var requested : request.lines()) {
            var line = priceLine(requested, variants.get(requested.sku()), addons, images);
            lines.add(line);
            if (line.status() == BagQuote.Status.AVAILABLE || line.status() == BagQuote.Status.LIMITED) {
                var quantity = Math.min(line.quantity(), line.maxQuantity());
                items += line.pricePaise() * quantity;
                addonTotal += line.addonPricePaise() * quantity;
                mrp += (line.mrpPaise() + line.addonPricePaise()) * quantity;
            }
        }
        return new BagQuote(lines, items, addonTotal, mrp, items + addonTotal);
    }

    private static BagQuote.Line priceLine(
            BagQuoteRequest.Line requested,
            @Nullable VariantRow variant,
            Map<String, AddonRow> addons,
            List<ImageRow> images) {
        if (variant == null) {
            return unavailable(requested);
        }
        AddonRow addon = null;
        if (requested.addonCode() != null) {
            addon = addons.get(variant.productId() + "|" + requested.addonCode());
            if (addon == null) {
                return unavailable(requested);
            }
        }
        var max = Math.min(MAX_QUANTITY, variant.stockQty());
        BagQuote.Status status;
        if (variant.stockQty() <= 0) {
            status = BagQuote.Status.OUT_OF_STOCK;
        } else if (requested.quantity() > variant.stockQty()) {
            status = BagQuote.Status.LIMITED;
        } else {
            status = BagQuote.Status.AVAILABLE;
        }
        var image = images.stream()
                .filter(i -> i.productId() == variant.productId())
                .filter(i -> i.colourName() == null || i.colourName().equals(variant.colourName()))
                .findFirst()
                .map(ImageRow::storageKey)
                .orElse(null);
        return new BagQuote.Line(
                variant.sku(),
                requested.addonCode(),
                status,
                requested.quantity(),
                Math.max(max, 0),
                Stock.stockLeft(variant.stockQty()),
                variant.slug(),
                variant.name(),
                variant.colourName(),
                variant.colourHex(),
                variant.size(),
                variant.pricePaise(),
                variant.mrpPaise(),
                addon == null ? null : addon.name(),
                addon == null ? 0 : addon.pricePaise(),
                addon == null ? null : AddonInput.valueOf(Objects.requireNonNull(addon.input())),
                image);
    }

    private static BagQuote.Line unavailable(BagQuoteRequest.Line requested) {
        return new BagQuote.Line(
                requested.sku(),
                requested.addonCode(),
                BagQuote.Status.UNAVAILABLE,
                requested.quantity(),
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                0,
                null,
                null);
    }
}
