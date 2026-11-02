package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Orders and the stitching price list. Requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
class OrderController {

    /**
     * Move items to a production stage.
     *
     * @param itemIds the items to move; empty moves every item of the order
     */
    record StageMove(
            @NotNull Long stageId, @NotNull @Size(max = 30) List<Long> itemIds) {}

    private final OrderService orders;
    private final ServiceProductService products;
    private final TailoringImage images;

    OrderController(OrderService orders, ServiceProductService products, TailoringImage images) {
        this.orders = orders;
        this.products = products;
        this.images = images;
    }

    // --- orders -------------------------------------------------------------------------------------------------

    @GetMapping("/orders")
    OrderPage listOrders(
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(defaultValue = "ALL") OrderService.Filter filter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
            @RequestParam(required = false) @Nullable Long clientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return orders.list(
                q, filter, from, to, clientId, ClientController.page(page), ClientController.pageSize(pageSize));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    Order createOrder(@Valid @RequestBody OrderForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        form.items().forEach(item -> images.requireUploaded(TailoringImage.Purpose.CLOTH, item.clothPhotoKey()));
        return orders.create(form, ClientController.actor(jwt));
    }

    @GetMapping("/orders/{id}")
    Order getOrder(@PathVariable long id) {
        return orders.get(id);
    }

    @PostMapping("/orders/{id}/stage")
    Order moveOrderItems(
            @PathVariable long id, @Valid @RequestBody StageMove move, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return orders.moveItems(id, move.stageId(), move.itemIds(), ClientController.actor(jwt));
    }

    @PostMapping("/orders/{id}/deliver")
    Order deliverOrder(@PathVariable long id, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return orders.deliver(id, ClientController.actor(jwt));
    }

    @PostMapping("/orders/{id}/cancel")
    Order cancelOrder(@PathVariable long id, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return orders.cancel(id, ClientController.actor(jwt));
    }

    @PutMapping("/orders/{id}/schedule")
    Order rescheduleOrder(@PathVariable long id, @Valid @RequestBody OrderSchedule schedule) {
        return orders.reschedule(id, schedule);
    }

    @PostMapping("/orders/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    Order addOrderPayment(
            @PathVariable long id, @Valid @RequestBody PaymentForm form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return orders.addPayment(id, form, ClientController.actor(jwt));
    }

    // --- price list ---------------------------------------------------------------------------------------------

    @GetMapping("/service-products")
    List<ServiceProduct> listServiceProducts(
            @RequestParam(required = false) @Nullable Long templateId,
            @RequestParam(required = false) @Nullable String q) {
        return products.list(templateId, q);
    }

    @PostMapping("/service-products")
    @ResponseStatus(HttpStatus.CREATED)
    ServiceProduct createServiceProduct(@Valid @RequestBody ServiceProductForm form) {
        images.requireUploaded(TailoringImage.Purpose.PRODUCT, form.photoKey());
        return products.save(null, form);
    }

    @PutMapping("/service-products/{id}")
    ServiceProduct updateServiceProduct(@PathVariable long id, @Valid @RequestBody ServiceProductForm form) {
        if (!Objects.equals(form.photoKey(), products.get(id).photoKey())) {
            images.requireUploaded(TailoringImage.Purpose.PRODUCT, form.photoKey());
        }
        return products.save(id, form);
    }

    @DeleteMapping("/service-products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteServiceProduct(@PathVariable long id) {
        products.remove(id);
    }
}
