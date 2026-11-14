package com.vaabhi.catalog;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store/catalog")
class StoreCatalogFeedController {

    private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

    private final MetaCatalogFeed feed;

    StoreCatalogFeedController(MetaCatalogFeed feed) {
        this.feed = feed;
    }

    @Operation(
            summary = "Product feed for Meta Commerce Manager",
            description = "Every published product, one row per variant, in Meta's catalogue CSV format. Commerce "
                    + "Manager fetches it on a schedule; the same catalogue feeds Instagram Shopping and WhatsApp.")
    @GetMapping(value = "/meta-feed.csv", produces = "text/csv")
    ResponseEntity<String> metaFeed() {
        return ResponseEntity.ok()
                .contentType(CSV)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(feed.csv());
    }
}
