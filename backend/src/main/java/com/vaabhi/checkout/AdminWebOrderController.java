package com.vaabhi.checkout;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Online orders for the shop (ADMIN role). */
@RestController
@RequestMapping("/api/v1/admin/online-orders")
class AdminWebOrderController {

    private final CheckoutService service;

    AdminWebOrderController(CheckoutService service) {
        this.service = service;
    }

    /** The latest 100 online orders, newest first. */
    @GetMapping
    List<WebOrder> listOnlineOrders() {
        return service.recent(100);
    }
}
