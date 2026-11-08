-- Shipping online orders through Shiprocket, and following Razorpay refunds until the money reaches the customer.
--
-- shiprocket_order_id / shiprocket_shipment_id: set once the order is created in Shiprocket (a retry of "Ship with
-- Shiprocket" carries on from there). tracking_number holds the AWB, courier the courier Shiprocket assigned.
-- tracking_status: the courier's latest status text (from Shiprocket's webhook or a manual refresh).
-- refund_status: PENDING until Razorpay's refund.processed (PROCESSED) or refund.failed (FAILED) webhook.

ALTER TABLE web_order
    ADD COLUMN shiprocket_order_id    BIGINT UNIQUE,
    ADD COLUMN shiprocket_shipment_id BIGINT UNIQUE,
    ADD COLUMN shipping_label_url     VARCHAR(500),
    ADD COLUMN tracking_status        VARCHAR(120),
    ADD COLUMN tracking_updated_at    TIMESTAMPTZ,
    ADD COLUMN refund_status          VARCHAR(10) CHECK (refund_status IN ('PENDING', 'PROCESSED', 'FAILED'));

-- Refunds made before this migration were instant refunds Razorpay accepted.
UPDATE web_order SET refund_status = 'PROCESSED' WHERE refund_id IS NOT NULL;

CREATE INDEX web_order_tracking_idx ON web_order (tracking_number) WHERE tracking_number IS NOT NULL;
CREATE INDEX web_order_refund_idx ON web_order (refund_id) WHERE refund_id IS NOT NULL;
