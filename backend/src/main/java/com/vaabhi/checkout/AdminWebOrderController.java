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

/** Online orders for the shop (ADMIN role): list, ship, deliver, cancel (with a Razorpay refund when paid online). */
@RestController
@RequestMapping("/api/v1/admin/online-orders")
class AdminWebOrderController {

    private final CheckoutService service;

    AdminWebOrderController(CheckoutService service) {
        this.service = service;
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

    @PostMapping("/{number}/deliver")
    WebOrder deliverOnlineOrder(@PathVariable("number") String number) {
        return service.deliver(number);
    }

    @PostMapping("/{number}/cancel")
    WebOrder cancelOnlineOrder(@PathVariable("number") String number, @Valid @RequestBody CancelWebOrderForm form) {
        return service.cancel(number, form);
    }
}
