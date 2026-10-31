package com.vaabhi.catalog;

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

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(String slug) {
        return detailQueries.find(slug).orElseThrow(() -> new CatalogNotFoundException("Product", slug));
    }
}
