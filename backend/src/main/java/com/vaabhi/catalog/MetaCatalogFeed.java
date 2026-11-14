package com.vaabhi.catalog;

import com.vaabhi.settings.SettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The shop's catalogue in Meta's CSV feed format. One Meta catalogue serves both Instagram Shopping and the
 * WhatsApp Business catalogue, so publishing a product in the admin lists it on both once Commerce Manager next
 * fetches this feed, or at once when the Catalog API is set up ({@link MetaCatalogSync}). One row per active variant (colour × size), grouped by product. A product only appears once
 * at least one of its photos has a JPEG copy ({@link CatalogFeedImages}); Meta rejects items without a photo.
 */
@Component
@EnableConfigurationProperties(CatalogFeedProperties.class)
class MetaCatalogFeed {

    static final List<String> COLUMNS = List.of(
            "id",
            "item_group_id",
            "title",
            "description",
            "availability",
            "condition",
            "price",
            "sale_price",
            "link",
            "image_link",
            "additional_image_link",
            "brand",
            "color",
            "size",
            "quantity_to_sell_on_facebook",
            "product_type");

    /** The Catalog API takes at most 9 extra photos per item. */
    private static final int MAX_EXTRA_IMAGES = 9;

    private final JdbcClient jdbc;
    private final CatalogFeedProperties properties;
    private final SettingsService settings;

    MetaCatalogFeed(JdbcClient jdbc, CatalogFeedProperties properties, SettingsService settings) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.settings = settings;
    }

    private record ProductRow(
            long id,
            String slug,
            String name,
            @Nullable String subtitle,
            @Nullable String description,
            String categoryName) {}

    private record VariantRow(
            long productId,
            String sku,
            String colourName,
            @Nullable String size,
            BigDecimal priceRupees,
            BigDecimal mrpRupees,
            int stockQty) {}

    private record PhotoRow(
            long productId, String storageKey, @Nullable String colourName) {}

    /** One catalogue item (a variant); {@code fields} use Meta's field names, photos beyond the first as a list. */
    record Item(long productId, String id, Map<String, Object> fields) {}

    /** Every item the catalogue should hold right now. */
    List<Item> items() {
        var products = jdbc.sql("""
                        SELECT p.id, p.slug, p.name, p.subtitle, p.description, c.name AS category_name
                        FROM product p
                        JOIN category c ON c.id = p.category_id
                        WHERE p.status = 'PUBLISHED'
                        ORDER BY p.published_at DESC, p.id
                        """).query(ProductRow.class).list();
        var variants = jdbc.sql("""
                        SELECT v.product_id, v.sku, v.colour_name, v.size, v.price_rupees, v.mrp_rupees, v.stock_qty
                        FROM product_variant v
                        JOIN product p ON p.id = v.product_id
                        WHERE p.status = 'PUBLISHED' AND v.active
                        ORDER BY v.position, v.id
                        """).query(VariantRow.class).list().stream()
                .collect(Collectors.groupingBy(VariantRow::productId, LinkedHashMap::new, Collectors.toList()));
        var photos = jdbc.sql("""
                        SELECT m.product_id, m.storage_key, m.colour_name
                        FROM product_media m
                        JOIN product p ON p.id = m.product_id
                        WHERE p.status = 'PUBLISHED' AND m.kind = 'IMAGE' AND m.status = 'READY' AND m.feed_image
                        ORDER BY m.position, m.id
                        """).query(PhotoRow.class).list().stream()
                .collect(Collectors.groupingBy(PhotoRow::productId, LinkedHashMap::new, Collectors.toList()));
        var brand = settings.shopProfile().shopName();

        var items = new ArrayList<Item>();
        for (var product : products) {
            var productVariants = variants.getOrDefault(product.id(), List.of());
            var productPhotos = photos.getOrDefault(product.id(), List.of());
            if (productVariants.isEmpty() || productPhotos.isEmpty()) {
                continue;
            }
            boolean manyColours = productVariants.stream()
                            .map(VariantRow::colourName)
                            .distinct()
                            .count()
                    > 1;
            for (var variant : productVariants) {
                var images = imagesFor(productPhotos, variant.colourName());
                items.add(new Item(product.id(), variant.sku(), fields(product, variant, images, brand, manyColours)));
            }
        }
        return items;
    }

    /** The items as Meta's CSV feed: every column on every row, extra photos comma-separated. */
    String csv() {
        var out = new StringBuilder();
        line(out, COLUMNS);
        for (var item : items()) {
            line(
                    out,
                    COLUMNS.stream()
                            .map(column -> text(item.fields().get(column)))
                            .toList());
        }
        return out.toString();
    }

    private static String text(@Nullable Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> fields(
            ProductRow product, VariantRow variant, List<String> images, String brand, boolean manyColours) {
        boolean onSale = variant.mrpRupees().compareTo(variant.priceRupees()) > 0;
        var link = properties.storefrontUrl() + "/p/" + product.slug()
                + (manyColours ? "?colour=" + URLEncoder.encode(variant.colourName(), StandardCharsets.UTF_8) : "");
        // Empty strings rather than missing keys: Meta only clears a field (e.g. a sale that ended) on "".
        var fields = new LinkedHashMap<String, Object>();
        fields.put("id", variant.sku());
        fields.put("item_group_id", product.slug());
        fields.put("title", truncate(product.name(), 150));
        fields.put("description", truncate(description(product), 9999));
        fields.put("availability", variant.stockQty() > 0 ? "in stock" : "out of stock");
        fields.put("condition", "new");
        fields.put("price", money(onSale ? variant.mrpRupees() : variant.priceRupees()));
        fields.put("sale_price", onSale ? money(variant.priceRupees()) : "");
        fields.put("link", link);
        fields.put("image_link", images.getFirst());
        fields.put("additional_image_link", List.copyOf(images.subList(1, images.size())));
        fields.put("brand", brand);
        fields.put("color", variant.colourName());
        fields.put("size", variant.size() == null ? "" : variant.size());
        fields.put("quantity_to_sell_on_facebook", Math.max(variant.stockQty(), 0));
        fields.put("product_type", product.categoryName());
        return fields;
    }

    /** The variant's own colour photos first; without any, every photo of the product. */
    private List<String> imagesFor(List<PhotoRow> photos, String colour) {
        var own = photos.stream()
                .filter(p -> p.colourName() != null && p.colourName().equalsIgnoreCase(colour))
                .toList();
        var chosen = new ArrayList<>(own.isEmpty() ? photos : own);
        if (!own.isEmpty()) {
            photos.stream().filter(p -> p.colourName() == null).forEach(chosen::add);
        }
        return chosen.stream()
                .limit(1 + MAX_EXTRA_IMAGES)
                .map(p -> properties.mediaUrl() + "/" + CatalogFeedImages.feedImageKey(p.storageKey()))
                .toList();
    }

    private static String description(ProductRow product) {
        if (product.description() != null && !product.description().isBlank()) {
            return product.description().strip();
        }
        if (product.subtitle() != null && !product.subtitle().isBlank()) {
            return product.subtitle().strip();
        }
        return product.name();
    }

    static String money(BigDecimal rupees) {
        return rupees.setScale(2, RoundingMode.HALF_UP).toPlainString() + " INR";
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static void line(StringBuilder out, List<String> cells) {
        out.append(cells.stream().map(MetaCatalogFeed::cell).collect(Collectors.joining(",")))
                .append('\n');
    }

    /** Every cell quoted, quotes doubled, so commas and line breaks in descriptions are safe. */
    static String cell(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
