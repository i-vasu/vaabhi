package com.vaabhi.storefront;

import com.vaabhi.settings.GarmentTemplate;
import java.util.List;

/**
 * One garment's measurement form for the storefront account page, from the shop's garment templates
 * (Settings → Garments), so customers give the same measurements the shop takes in person.
 *
 * @param id the template id; customers' measurements are stored under it
 */
public record ShopMeasurementForm(long id, String name, List<MeasurementField> fields) {

    /** @param unit "in", "cm", or empty for free text (e.g. neck style) */
    public record MeasurementField(String key, String label, String unit) {}

    static ShopMeasurementForm of(GarmentTemplate template) {
        return new ShopMeasurementForm(
                template.id() == null ? 0 : template.id(),
                template.name(),
                template.fields().stream()
                        .map(f -> new MeasurementField(f.key(), f.label(), f.unit()))
                        .toList());
    }

    /** The storefront sells women's wear: ladies' and everyone's templates. */
    static boolean forStorefront(GarmentTemplate template) {
        return template.id() != null
                && (template.businessType() == GarmentTemplate.Audience.LADIES
                        || template.businessType() == GarmentTemplate.Audience.ALL);
    }
}
