package com.vaabhi.catalog;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Storefront catalogue reads. Only published products are ever returned. */
@Service
public class ProductService {

    private final CategoryService categoryService;
    private final FacetCatalog facetCatalog;
    private final ProductListingQueries listingQueries;
    private final ProductDetailQueries detailQueries;

    ProductService(
            CategoryService categoryService,
            FacetCatalog facetCatalog,
            ProductListingQueries listingQueries,
            ProductDetailQueries detailQueries) {
        this.categoryService = categoryService;
        this.facetCatalog = facetCatalog;
        this.listingQueries = listingQueries;
        this.detailQueries = detailQueries;
    }

    @Transactional(readOnly = true)
    public ProductListingResponse listProducts(ListingQuery query) {
        var category = categoryService.listCategories().stream()
                .filter(c -> c.slug().equals(query.categorySlug()))
                .findFirst()
                .orElseThrow(() -> new CatalogNotFoundException("Category", query.categorySlug()));
        return listingQueries.list(category, query, facetCatalog.definitions());
    }

    /** Products from every category matching the text (all of them without text) and the filters. */
    @Transactional(readOnly = true)
    public SearchResponse search(@Nullable String text, ListingQuery query) {
        return listingQueries.search(text, query, facetCatalog.definitions());
    }

    /** Up to {@code limit} published products with the badge, most popular first. */
    @Transactional(readOnly = true)
    public List<ProductSummary> featured(Badge badge, int limit) {
        return listingQueries.featured(badge, limit);
    }

    @Transactional(readOnly = true)
    public NewArrivalsPage newArrivals(int page, int pageSize) {
        return listingQueries.newArrivals(page, pageSize);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(String slug) {
        return detailQueries.find(slug).orElseThrow(() -> new CatalogNotFoundException("Product", slug));
    }
}
