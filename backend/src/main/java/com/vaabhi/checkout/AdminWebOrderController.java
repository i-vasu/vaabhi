package com.vaabhi.checkout;

import jakarta.validation.Valid;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Online orders for the shop (ADMIN role): list, ship (by hand or through Shiprocket),
 * track, deliver, cancel (with a Razorpay refund when paid online). */
@RestController
@RequestMapping("/api/v1/admin/online-orders")
class AdminWebOrderController {

    private final CheckoutService service;
    private final ShiprocketShipping shiprocket;

    AdminWebOrderController(CheckoutService service, ShiprocketShipping shiprocket) {
        this.service = service;
        this.shiprocket = shiprocket;
    }

    /** The latest 100 online orders, newest first; optionally only one status. */
    @GetMapping
    List<WebOrder> listOnlineOrders(@RequestParam(name = "status", required = false) WebOrder.@Nullable Status status) {
        return service.recent(status, 100);
    }

    @PostMapping("/{number}/ship")
    WebOrder shipOnlineOrder(@PathVariable("number") String number, @Valid @RequestBody ShipmentForm form) {
        return service.ship(number, form);
    }

    /** Ships through Shiprocket: creates the order there, assigns a courier, schedules the pickup, makes the label. */
    @PostMapping("/{number}/shiprocket")
    WebOrder shipOnlineOrderWithShiprocket(@PathVariable("number") String number) {
        return shiprocket.ship(number);
    }

    /** Fetches the courier's latest status from Shiprocket now. */
    @PostMapping("/{number}/tracking")
    WebOrder refreshOnlineOrderTracking(@PathVariable("number") String number) {
        return shiprocket.refreshTracking(number);
    }

    @PostMapping("/{number}/deliver")
    WebOrder deliverOnlineOrder(@PathVariable("number") String number) {
        return service.deliver(number);
    }

    @PostMapping("/{number}/cancel")
    WebOrder cancelOnlineOrder(@PathVariable("number") String number, @Valid @RequestBody CancelWebOrderForm form) {
        return service.cancel(number, form);
    }
}
