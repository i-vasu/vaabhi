package com.vaabhi.catalog;

import com.vaabhi.catalog.CatalogValidationException.FieldError;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin catalogue management: create and edit products (always as drafts), then publish, unpublish or archive. */
@Service
public class AdminProductService {

    static final List<String> SIZES = List.of("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL", "Free size");
    private static final String SKU_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminProductStore store;
    private final FacetCatalog facetCatalog;
    private final CategoryService categoryService;

    AdminProductService(AdminProductStore store, FacetCatalog facetCatalog, CategoryService categoryService) {
        this.store = store;
        this.facetCatalog = facetCatalog;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public AdminProductPage list(
            @Nullable ProductStatus status, @Nullable String category, @Nullable String query, int page, int pageSize) {
        return store.list(status, category, query, page, pageSize);
    }

    @Transactional(readOnly = true)
    public AdminProduct get(long id) {
        return load(id);
    }

    /** Non-transactional read used inside the write methods' own transactions. */
    private AdminProduct load(long id) {
        return store.find(id).orElseThrow(() -> notFound(id));
    }

    @Transactional(readOnly = true)
    public CatalogOptions options() {
        var definitions = facetCatalog.definitions();
        return new CatalogOptions(
                categoryService.listCategories(),
                definitions.facets().stream()
                        .map(f -> new CatalogOptions.FacetOption(
                                f.code(),
                                f.name(),
                                f.values().stream()
                                        .map(v -> new AttributeResponse.AttributeValue(v.code(), v.name()))
                                        .toList()))
                        .toList(),
                definitions.colours().stream()
                        .map(c -> new CatalogOptions.ColourOption(c.code(), c.name(), c.hex()))
                        .toList(),
                SIZES);
    }

    /** Creates a draft. */
    @Transactional
    public AdminProduct create(ProductForm form) {
        var prepared = prepare(form, null);
        var id = store.insert(form, prepared.slug(), prepared.categoryId());
        store.replaceChildren(id, prepared.variants(), prepared.facetValues(), form.addons());
        return load(id);
    }

    /** Replaces everything but the status; a published product stays published with the new details. */
    @Transactional
    public AdminProduct update(long id, ProductForm form) {
        var prepared = prepare(form, id);
        if (!store.update(id, form, prepared.slug(), prepared.categoryId())) {
            throw notFound(id);
        }
        store.replaceChildren(id, prepared.variants(), prepared.facetValues(), form.addons());
        if (store.find(id).map(AdminProduct::status).orElseThrow() == ProductStatus.PUBLISHED) {
            requirePublishable(id);
        }
        return load(id);
    }

    @Transactional
    public AdminProduct changeStatus(long id, ProductStatus status) {
        if (status == ProductStatus.PUBLISHED) {
            requirePublishable(id);
        }
        if (!store.setStatus(id, status)) {
            throw notFound(id);
        }
        return load(id);
    }

    private void requirePublishable(long id) {
        load(id);
        if (store.activeVariantCount(id) == 0) {
            throw new CatalogValidationException(
                    HttpStatus.BAD_REQUEST, "variants", "Add at least one active variant before publishing");
        }
    }

    private static CatalogNotFoundException notFound(long id) {
        return new CatalogNotFoundException("Product", String.valueOf(id));
    }

    // --- validation and normalisation ----------------------------------------------------------------------

    private record Prepared(
            String slug, long categoryId, List<VariantForm> variants, Map<Long, List<String>> facetValues) {}

    private Prepared prepare(ProductForm form, @Nullable Long productId) {
        var errors = new ArrayList<FieldError>();
        var categoryId = store.categoryId(form.categorySlug()).orElse(null);
        if (categoryId == null) {
            errors.add(new FieldError("categorySlug", "Unknown category"));
        }
        var facetValues = facetValues(form.attributes(), errors);
        var variants = variants(form.variants(), errors);
        addonCodes(form.addons(), errors);
        if (!errors.isEmpty()) {
            throw new CatalogValidationException(errors);
        }

        var slug = form.slug() == null || form.slug().isBlank()
                ? uniqueSlug(slugify(form.name()), productId)
                : form.slug();
        if (store.slugTaken(slug, productId)) {
            throw new CatalogValidationException(HttpStatus.CONFLICT, "slug", "Another product already uses this URL");
        }
        var taken =
                store.skusUsedElsewhere(variants.stream().map(VariantForm::sku).toList(), productId);
        if (!taken.isEmpty()) {
            throw new CatalogValidationException(
                    HttpStatus.CONFLICT,
                    "variants",
                    "SKU already used by another product: " + String.join(", ", taken));
        }
        return new Prepared(slug, Objects.requireNonNull(categoryId), variants, facetValues);
    }

    private Map<Long, List<String>> facetValues(Map<String, List<String>> attributes, List<FieldError> errors) {
        var definitions = facetCatalog.definitions();
        Map<Long, List<String>> byFacetId = new HashMap<>();
        attributes.forEach((code, values) -> {
            var facet = definitions.facet(code);
            if (facet.isEmpty()) {
                errors.add(new FieldError("attributes." + code, "Unknown attribute"));
                return;
            }
            for (var value : values) {
                if (facet.get().value(value).isEmpty()) {
                    errors.add(new FieldError(
                            "attributes." + code, "Unknown " + facet.get().name() + " '" + value + "'"));
                }
            }
            if (!values.isEmpty()) {
                byFacetId.put(facet.get().id(), List.copyOf(new HashSet<>(values)));
            }
        });
        return byFacetId;
    }

    /** Fills in missing SKUs and checks prices, colour families and duplicates. */
    private List<VariantForm> variants(List<VariantForm> variants, List<FieldError> errors) {
        var colourFamilies = new HashSet<String>();
        facetCatalog.definitions().colours().forEach(c -> colourFamilies.add(c.code()));
        var combinations = new HashSet<String>();
        var skus = new HashSet<String>();
        var result = new ArrayList<VariantForm>();
        for (var i = 0; i < variants.size(); i++) {
            var v = variants.get(i);
            var field = "variants[" + i + "]";
            if (v.mrpPaise() < v.pricePaise()) {
                errors.add(new FieldError(field + ".mrpPaise", "MRP must be at least the selling price"));
            }
            if (!colourFamilies.contains(v.colourFamily())) {
                errors.add(new FieldError(field + ".colourFamily", "Unknown colour family"));
            }
            var size = v.size() == null || v.size().isBlank() ? null : v.size().trim();
            if (!combinations.add(v.colourName().trim().toLowerCase(Locale.ROOT) + "|" + size)) {
                errors.add(new FieldError(field, "Two variants have the same colour and size"));
            }
            var sku = v.sku() == null || v.sku().isBlank() ? newSku(size) : v.sku();
            if (!skus.add(sku)) {
                errors.add(new FieldError(field + ".sku", "Duplicate SKU " + sku));
            }
            result.add(new VariantForm(
                    sku,
                    v.colourName(),
                    v.colourHex(),
                    v.colourFamily(),
                    size,
                    v.pricePaise(),
                    v.mrpPaise(),
                    v.stockQty(),
                    v.active()));
        }
        return result;
    }

    private static void addonCodes(List<AddonForm> addons, List<FieldError> errors) {
        var codes = new HashSet<String>();
        for (var i = 0; i < addons.size(); i++) {
            if (!codes.add(addons.get(i).code())) {
                errors.add(new FieldError("addons[" + i + "].code", "Duplicate add-on code"));
            }
        }
    }

    private String uniqueSlug(String base, @Nullable Long productId) {
        var slug = base;
        for (var n = 2; store.slugTaken(slug, productId); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }

    /** "Kanjeevaram Silk Saree – Rani Pink" → "kanjeevaram-silk-saree-rani-pink". */
    static String slugify(String name) {
        var ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        var slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        slug = trimDashes(slug);
        if (slug.length() > 150) {
            slug = trimDashes(slug.substring(0, 150));
        }
        return slug.isEmpty() ? "product" : slug;
    }

    private static String trimDashes(String value) {
        var start = 0;
        var end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }

    /** e.g. VB-K7M2QX-M; random, so it never collides with a deleted variant's SKU in practice. */
    static String newSku(@Nullable String size) {
        var code = new StringBuilder("VB-");
        for (var i = 0; i < 6; i++) {
            code.append(SKU_ALPHABET.charAt(RANDOM.nextInt(SKU_ALPHABET.length())));
        }
        if (size != null) {
            code.append('-').append(size.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", ""));
        }
        return code.toString();
    }
}
