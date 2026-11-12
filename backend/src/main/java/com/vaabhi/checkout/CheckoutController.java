package com.vaabhi.checkout;

import com.vaabhi.money.CreditNoteService;
import com.vaabhi.money.StoreCreditNote;
import com.vaabhi.money.StoreInvoice;
import com.vaabhi.money.WebInvoiceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Checkout and order history for the signed-in customer (CUSTOMER role). */
@RestController
@RequestMapping("/api/v1/customer")
class CheckoutController {

    private final CheckoutService service;
    private final WebOrderReturns returns;
    private final WebInvoiceService webInvoices;
    private final CreditNoteService creditNotes;

    CheckoutController(
            CheckoutService service,
            WebOrderReturns returns,
            WebInvoiceService webInvoices,
            CreditNoteService creditNotes) {
        this.service = service;
        this.returns = returns;
        this.webInvoices = webInvoices;
        this.creditNotes = creditNotes;
    }

    /** Places the order and returns what Razorpay Checkout needs to take the payment. */
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    CheckoutStart startCheckout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckoutRequest request) {
        return service.start(customerId(jwt), request);
    }

    /** Confirms the payment Razorpay Checkout returned (signature checked on the server). */
    @PostMapping("/orders/{number}/payment")
    WebOrder confirmPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("number") String number,
            @Valid @RequestBody PaymentConfirmation payment) {
        return service.confirm(customerId(jwt), number, payment);
    }

    /** "Pay now" on an unpaid online order: what Razorpay Checkout needs to take the payment again. */
    @PostMapping("/orders/{number}/pay")
    CheckoutStart payOrderAgain(@AuthenticationPrincipal Jwt jwt, @PathVariable("number") String number) {
        return service.payAgain(customerId(jwt), number);
    }

    /** Cancels an order that has not been shipped yet; an online payment is refunded in full. */
    @PostMapping("/orders/{number}/cancel")
    WebOrder cancelMyOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("number") String number,
            @Valid @RequestBody CancelWebOrderForm form) {
        return service.cancelByCustomer(customerId(jwt), number, form);
    }

    /** Asks to return items of a delivered order (within the return window). */
    @PostMapping("/orders/{number}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    WebOrder requestReturn(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("number") String number,
            @Valid @RequestBody ReturnRequestForm form) {
        return returns.request(customerId(jwt), number, form);
    }

    @GetMapping("/orders")
    List<WebOrder> listMyOrders(@AuthenticationPrincipal Jwt jwt) {
        return service.orders(customerId(jwt));
    }

    @GetMapping("/orders/{number}")
    WebOrder getMyOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable("number") String number) {
        return service.order(customerId(jwt), number);
    }

    /** The tax invoice of one of the customer's orders, once it has shipped. */
    @GetMapping("/orders/{number}/invoice")
    StoreInvoice getMyInvoice(@AuthenticationPrincipal Jwt jwt, @PathVariable("number") String number) {
        return webInvoices
                .forCustomer(number, customerId(jwt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No invoice for this order"));
    }

    /** The credit notes on one of the customer's orders (a cancel or an accepted return); empty when none. */
    @GetMapping("/orders/{number}/credit-notes")
    List<StoreCreditNote> getMyCreditNotes(@AuthenticationPrincipal Jwt jwt, @PathVariable("number") String number) {
        return creditNotes.forCustomer(number, customerId(jwt));
    }

    private static long customerId(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again");
        }
    }
}
