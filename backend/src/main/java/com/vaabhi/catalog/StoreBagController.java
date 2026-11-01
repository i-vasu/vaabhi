package com.vaabhi.catalog;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: prices the shopper's bag. Read-only despite POST (the bag is too long for a query string). */
@RestController
@RequestMapping("/api/v1/store/bag")
class StoreBagController {

    private final BagQuoteService service;

    StoreBagController(BagQuoteService service) {
        this.service = service;
    }

    @PostMapping("/quote")
    BagQuote quoteBag(@Valid @RequestBody BagQuoteRequest request) {
        return service.quote(request);
    }
}
