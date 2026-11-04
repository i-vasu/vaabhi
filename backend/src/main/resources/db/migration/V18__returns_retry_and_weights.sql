-- Returns with partial refunds, paying again for an unpaid order, and parcel weights for the courier.

-- The packed weight of one piece, for Shiprocket; empty means the shop default (SHIPROCKET_WEIGHT_KG).
ALTER TABLE product
    ADD COLUMN shipping_weight_grams INT CHECK (shipping_weight_grams BETWEEN 10 AND 30000);

-- When the customer last opened Razorpay for the order (checkout or "Pay now"); the payment check waits from here.
ALTER TABLE web_order ADD COLUMN payment_started_at TIMESTAMPTZ;
UPDATE web_order SET payment_started_at = created_at WHERE payment_method = 'ONLINE';

-- A return of some or all of a delivered order's items.
-- status: REQUESTED (by the customer) → APPROVED (the shop agreed; pickup optional) → REFUNDED (items received,
-- stock put back, money refunded), or REJECTED.
-- refund_method: RAZORPAY (partial refund of the online payment) or MANUAL (cash on delivery orders: the shop pays
-- by UPI or bank transfer and records the reference in refund_id).
CREATE TABLE web_order_return (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id               BIGINT         NOT NULL REFERENCES web_order (id) ON DELETE CASCADE,
    number                 VARCHAR(24)    NOT NULL UNIQUE,
    status                 VARCHAR(10)    NOT NULL DEFAULT 'REQUESTED'
                                          CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'REFUNDED')),
    reason                 VARCHAR(500)   NOT NULL,
    shop_note              VARCHAR(200),
    items_rupees           NUMERIC(12, 2) NOT NULL CHECK (items_rupees >= 0),
    refund_rupees          NUMERIC(12, 2) CHECK (refund_rupees >= 0),
    refund_method          VARCHAR(10)    CHECK (refund_method IN ('RAZORPAY', 'MANUAL')),
    refund_id              VARCHAR(80),
    refund_status          VARCHAR(10)    CHECK (refund_status IN ('PENDING', 'PROCESSED', 'FAILED')),
    shiprocket_order_id    BIGINT UNIQUE,
    shiprocket_shipment_id BIGINT UNIQUE,
    pickup_courier         VARCHAR(60),
    pickup_awb             VARCHAR(60),
    pickup_status          VARCHAR(120),
    requested_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    decided_at             TIMESTAMPTZ,
    refunded_at            TIMESTAMPTZ,
    CHECK (status <> 'REFUNDED' OR (refund_rupees IS NOT NULL AND refund_method IS NOT NULL))
);

CREATE INDEX web_order_return_order_idx ON web_order_return (order_id, requested_at);
CREATE INDEX web_order_return_open_idx ON web_order_return (status) WHERE status IN ('REQUESTED', 'APPROVED');
CREATE INDEX web_order_return_refund_idx ON web_order_return (refund_id) WHERE refund_id IS NOT NULL;
CREATE INDEX web_order_return_awb_idx ON web_order_return (pickup_awb) WHERE pickup_awb IS NOT NULL;

CREATE TABLE web_order_return_line (
    return_id     BIGINT NOT NULL REFERENCES web_order_return (id) ON DELETE CASCADE,
    order_line_id BIGINT NOT NULL REFERENCES web_order_line (id) ON DELETE CASCADE,
    quantity      INT    NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (return_id, order_line_id)
);
