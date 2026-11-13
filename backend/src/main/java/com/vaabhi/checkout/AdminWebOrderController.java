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

/**
 * Online orders for the shop (Orders permission): read, ship (by hand or through Shiprocket), track, deliver and
 * handle returns. Cancelling and refunding are for the owner alone (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin/online-orders")
class AdminWebOrderController {

    private final CheckoutService service;
    private final ShiprocketShipping shiprocket;
    private final WebOrderReturns returns;

    AdminWebOrderController(CheckoutService service, ShiprocketShipping shiprocket, WebOrderReturns returns) {
        this.service = service;
        this.shiprocket = shiprocket;
        this.returns = returns;
    }

    /** The latest 100 online orders, newest first; optionally only one status, or only those with an open return. */
    @GetMapping
    List<WebOrder> listOnlineOrders(
            @RequestParam(name = "status", required = false) WebOrder.@Nullable Status status,
            @RequestParam(name = "openReturns", defaultValue = "false") boolean openReturns) {
        return service.recent(status, openReturns, 100);
    }

    /** One online order, for its page in the dashboard. */
    @GetMapping("/{number}")
    WebOrder getOnlineOrder(@PathVariable("number") String number) {
        return service.shopOrder(number);
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

    /** Sends a cancelled order's refund again, when Razorpay refused it or it never got there. */
    @PostMapping("/{number}/refund")
    WebOrder retryOnlineOrderRefund(@PathVariable("number") String number) {
        return service.retryRefund(number);
    }

    @PostMapping("/returns/{returnNumber}/approve")
    WebOrder approveReturn(
            @PathVariable("returnNumber") String returnNumber, @Valid @RequestBody ReturnDecisionForm form) {
        return returns.approve(returnNumber, form);
    }

    @PostMapping("/returns/{returnNumber}/reject")
    WebOrder rejectReturn(
            @PathVariable("returnNumber") String returnNumber, @Valid @RequestBody ReturnDecisionForm form) {
        return returns.reject(returnNumber, form);
    }

    /** Books the courier to collect the return from the customer (through Shiprocket). */
    @PostMapping("/returns/{returnNumber}/pickup")
    WebOrder bookReturnPickup(@PathVariable("returnNumber") String returnNumber) {
        return shiprocket.bookReturnPickup(returnNumber);
    }

    /** The items are back: stock is put back and the amount refunded (Razorpay, or recorded for COD). */
    @PostMapping("/returns/{returnNumber}/refund")
    WebOrder refundReturn(
            @PathVariable("returnNumber") String returnNumber, @Valid @RequestBody ReturnRefundForm form) {
        return returns.refund(returnNumber, form);
    }
}
