-- Online orders after checkout: cash on delivery, shipping and delivery, cancellation with a Razorpay refund.
--
-- status: PENDING_PAYMENT (online, waiting for Razorpay) → PLACED (paid online, or a COD order) → SHIPPED →
-- DELIVERED; CANCELLED from PLACED or SHIPPED; FAILED when Razorpay reports the payment failed.
-- payment_method: ONLINE (Razorpay) or COD (cash collected on delivery; paid_at is set then).

ALTER TABLE web_order DROP CONSTRAINT web_order_status_check;
ALTER TABLE web_order DROP CONSTRAINT web_order_check;

UPDATE web_order SET status = 'PLACED' WHERE status = 'PAID';

ALTER TABLE web_order
    ADD CONSTRAINT web_order_status_check
        CHECK (status IN ('PENDING_PAYMENT', 'FAILED', 'PLACED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    ADD COLUMN payment_method  VARCHAR(8)     NOT NULL DEFAULT 'ONLINE' CHECK (payment_method IN ('ONLINE', 'COD')),
    ADD COLUMN courier         VARCHAR(60),
    ADD COLUMN tracking_number VARCHAR(60),
    ADD COLUMN shipped_at      TIMESTAMPTZ,
    ADD COLUMN delivered_at    TIMESTAMPTZ,
    ADD COLUMN cancelled_at    TIMESTAMPTZ,
    ADD COLUMN cancel_reason   VARCHAR(200),
    ADD COLUMN refund_id       VARCHAR(40),
    ADD COLUMN refunded_rupees NUMERIC(12, 2) CHECK (refunded_rupees >= 0),
    ADD COLUMN refunded_at     TIMESTAMPTZ;

-- An online order past PENDING_PAYMENT was paid through Razorpay.
ALTER TABLE web_order
    ADD CONSTRAINT web_order_online_paid_check CHECK (
        payment_method <> 'ONLINE' OR status IN ('PENDING_PAYMENT', 'FAILED')
        OR (paid_at IS NOT NULL AND razorpay_payment_id IS NOT NULL));

CREATE INDEX web_order_status_idx ON web_order (status, created_at DESC);
