package com.vaabhi.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Shop settings for the admin dashboard. Requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/settings")
class SettingsController {

    private final SettingsService settings;
    private final NumberingService numbering;
    private final ShopListService lists;
    private final SettingsImage images;

    SettingsController(
            SettingsService settings, NumberingService numbering, ShopListService lists, SettingsImage images) {
        this.settings = settings;
        this.numbering = numbering;
        this.lists = lists;
        this.images = images;
    }

    /** The staff member's name from the access token, for "updated by". */
    private static @Nullable String actor(@Nullable Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("name");
    }

    // --- JSON sections ------------------------------------------------------------------------------------------

    @GetMapping("/shop-profile")
    ShopProfile getShopProfile() {
        return settings.shopProfile();
    }

    @PutMapping("/shop-profile")
    ShopProfile saveShopProfile(@Valid @RequestBody ShopProfile profile, @AuthenticationPrincipal @Nullable Jwt jwt) {
        if (!Objects.equals(profile.logoKey(), settings.shopProfile().logoKey())) {
            images.requireUploaded(SettingsImage.Purpose.LOGO, profile.logoKey());
        }
        return settings.save(SettingsService.SHOP_PROFILE, profile, actor(jwt));
    }

    @GetMapping("/billing")
    BillingSettings getBilling() {
        return settings.billing();
    }

    @PutMapping("/billing")
    BillingSettings saveBilling(
            @Valid @RequestBody BillingSettings billing, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return settings.save(SettingsService.BILLING, billing, actor(jwt));
    }

    @GetMapping("/documents")
    DocumentSettings getDocuments() {
        return settings.documents();
    }

    @PutMapping("/documents")
    DocumentSettings saveDocuments(
            @Valid @RequestBody DocumentSettings documents, @AuthenticationPrincipal @Nullable Jwt jwt) {
        if (!Objects.equals(documents.upiQrKey(), settings.documents().upiQrKey())) {
            images.requireUploaded(SettingsImage.Purpose.UPI_QR, documents.upiQrKey());
        }
        return settings.save(SettingsService.DOCUMENTS, documents, actor(jwt));
    }

    @GetMapping("/orders")
    OrderSettings getOrderSettings() {
        return settings.orders();
    }

    @PutMapping("/orders")
    OrderSettings saveOrderSettings(
            @Valid @RequestBody OrderSettings orders, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return settings.save(SettingsService.ORDERS, orders, actor(jwt));
    }

    @GetMapping("/calendar")
    CalendarSettings getCalendar() {
        return settings.calendar();
    }

    @PutMapping("/calendar")
    CalendarSettings saveCalendar(
            @Valid @RequestBody CalendarSettings calendar, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return settings.save(SettingsService.CALENDAR, calendar, actor(jwt));
    }

    @GetMapping("/notifications")
    NotificationSettings getNotificationSettings() {
        return settings.notifications();
    }

    @PutMapping("/notifications")
    NotificationSettings saveNotificationSettings(
            @Valid @RequestBody NotificationSettings notifications, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return settings.save(SettingsService.NOTIFICATIONS, notifications, actor(jwt));
    }

    @GetMapping("/printing")
    PrintSettings getPrintSettings() {
        return settings.printing();
    }

    @PutMapping("/printing")
    PrintSettings savePrintSettings(
            @Valid @RequestBody PrintSettings printing, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return settings.save(SettingsService.PRINTING, printing, actor(jwt));
    }

    @GetMapping("/messages")
    MessageTemplates getMessageTemplates() {
        return settings.messages();
    }

    @PutMapping("/messages")
    MessageTemplates saveMessageTemplates(
            @Valid @RequestBody MessageTemplates messages, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return settings.save(SettingsService.MESSAGES, messages, actor(jwt));
    }

    @GetMapping("/watermark")
    WatermarkSettings getWatermark() {
        return settings.watermark();
    }

    @PutMapping("/watermark")
    WatermarkSettings saveWatermark(
            @Valid @RequestBody WatermarkSettings watermark, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return settings.save(SettingsService.WATERMARK, watermark, actor(jwt));
    }

    // --- numbering ----------------------------------------------------------------------------------------------

    @GetMapping("/numbering")
    List<NumberSequence> getNumbering() {
        return numbering.list();
    }

    @PutMapping("/numbering")
    List<NumberSequence> saveNumbering(@RequestBody @NotNull @Size(max = 20) List<@Valid NumberSequence> sequences) {
        return numbering.update(sequences);
    }

    // --- lists --------------------------------------------------------------------------------------------------

    @GetMapping("/stages")
    List<ProductionStage> getStages() {
        return lists.stages();
    }

    @PutMapping("/stages")
    List<ProductionStage> saveStages(@RequestBody @NotNull @Size(max = 20) List<@Valid ProductionStage> stages) {
        return lists.saveStages(stages);
    }

    @GetMapping("/delivery-packages")
    List<DeliveryPackage> getDeliveryPackages() {
        return lists.deliveryPackages();
    }

    @PutMapping("/delivery-packages")
    List<DeliveryPackage> saveDeliveryPackages(
            @RequestBody @NotNull @Size(max = 20) List<@Valid DeliveryPackage> packages) {
        return lists.saveDeliveryPackages(packages);
    }

    @GetMapping("/garment-templates")
    List<GarmentTemplate> getGarmentTemplates() {
        return lists.garmentTemplates();
    }

    @PostMapping("/garment-templates")
    @ResponseStatus(HttpStatus.CREATED)
    GarmentTemplate createGarmentTemplate(@Valid @RequestBody GarmentTemplate template) {
        return lists.saveGarmentTemplate(null, template);
    }

    @PutMapping("/garment-templates/{id}")
    GarmentTemplate updateGarmentTemplate(@PathVariable long id, @Valid @RequestBody GarmentTemplate template) {
        return lists.saveGarmentTemplate(id, template);
    }

    @DeleteMapping("/garment-templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGarmentTemplate(@PathVariable long id) {
        lists.removeGarmentTemplate(id);
    }

    // --- images -------------------------------------------------------------------------------------------------

    @PostMapping("/images")
    SettingsImageUpload startSettingsImageUpload(@Valid @RequestBody SettingsImageRequest request) {
        return images.start(request.purpose());
    }
}
