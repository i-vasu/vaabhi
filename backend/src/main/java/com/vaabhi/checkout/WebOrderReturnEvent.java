package com.vaabhi.checkout;

/** Something happened to a return that the customer hears about by email (sent after the change commits). */
record WebOrderReturnEvent(Type type, String orderNumber, String returnNumber) {

    enum Type {
        APPROVED,
        PICKUP_BOOKED,
        REJECTED,
        REFUNDED
    }
}
