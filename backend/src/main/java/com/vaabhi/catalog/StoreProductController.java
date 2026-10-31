package com.vaabhi.catalog;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/store")
class StoreProductController {

    /** Query parameters with a fixed meaning; every other parameter is read as a facet filter. */
    private static final Set<String> RESERVED =
            Set.of("colour", "size", "inStock", "priceMin", "priceMax", "sort", "page", "pageSize");

    private static final String FACET_HELP = "Comma-separated facet value codes (OR within the facet)";

    private final ProductService productService;

    StoreProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/categories/{slug}/products")
    @Parameters({
        @Parameter(name = "fabric", in = ParameterIn.QUERY, description = FACET_HELP, example = "silk,cotton"),
        @Parameter(name = "occasion", in = ParameterIn.QUERY, description = FACET_HELP, example = "wedding"),
        @Parameter(name = "weave", in = ParameterIn.QUERY, description = FACET_HELP, example = "banarasi"),
        @Parameter(name = "work", in = ParameterIn.QUERY, description = FACET_HELP, example = "zari")
    })
    ProductListingResponse listProducts(
            @PathVariable String slug,
            @Parameter(description = "Comma-separated colour family codes", example = "red,maroon")
                    @RequestParam(required = false)
                    @Nullable
                    String colour,
            @Parameter(description = "Comma-separated sizes", example = "M,L") @RequestParam(required = false) @Nullable
                    String size,
            @Parameter(description = "Only products with stock in the selected colour/size")
                    @RequestParam(defaultValue = "false")
                    boolean inStock,
            @Parameter(description = "Minimum price in paise") @RequestParam(required = false) @Nullable
                    Integer priceMin,
            @Parameter(description = "Maximum price in paise") @RequestParam(required = false) @Nullable
                    Integer priceMax,
            @Parameter(
                            schema =
                                    @Schema(
                                            allowableValues = {"popular", "new", "price_asc", "price_desc", "discount"},
                                            defaultValue = "popular"))
                    @RequestParam(defaultValue = "popular")
                    String sort,
            @Parameter(description = "1-based page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Products per page, at most 48") @RequestParam(defaultValue = "24") int pageSize,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> allParams) {
        if (page < 1) {
            throw badRequest("page must be 1 or more");
        }
        if (pageSize < 1 || pageSize > ListingQuery.MAX_PAGE_SIZE) {
            throw badRequest("pageSize must be between 1 and " + ListingQuery.MAX_PAGE_SIZE);
        }
        if ((priceMin != null && priceMin < 0)
                || (priceMax != null && priceMax < 0)
                || (priceMin != null && priceMax != null && priceMin > priceMax)) {
            throw badRequest("priceMin and priceMax must be positive and priceMin ≤ priceMax");
        }
        ProductSort productSort;
        try {
            productSort = ProductSort.fromParam(sort);
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        }

        Map<String, Set<String>> facets = new HashMap<>();
        allParams.forEach((name, values) -> {
            if (!RESERVED.contains(name)) {
                var codes = split(values);
                if (!codes.isEmpty()) {
                    facets.put(name, codes);
                }
            }
        });

        return productService.listProducts(new ListingQuery(
                slug,
                facets,
                split(colour == null ? List.of() : List.of(colour)),
                split(size == null ? List.of() : List.of(size)),
                inStock,
                priceMin,
                priceMax,
                productSort,
                page,
                pageSize));
    }

    @GetMapping("/products/{slug}")
    ProductDetailResponse getProduct(@PathVariable String slug) {
        return productService.getProduct(slug);
    }

    /** Accepts both {@code fabric=silk,cotton} and {@code fabric=silk&fabric=cotton}. */
    static Set<String> split(List<String> values) {
        var codes = new LinkedHashSet<String>();
        for (var value : values) {
            Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(code -> !code.isEmpty())
                    .forEach(codes::add);
        }
        return codes;
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
