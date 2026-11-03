-- Orders placed on the storefront and paid online with Razorpay. Prices are copied onto the order when it is
-- placed, so later price changes never alter what the customer was charged.
-- status: PENDING_PAYMENT (Razorpay order created, waiting for the payment) → PAID; FAILED when Razorpay reports
-- the payment failed (the customer can try again on a new order).

CREATE TABLE web_order (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number              VARCHAR(20)   NOT NULL UNIQUE,
    customer_id         BIGINT        NOT NULL REFERENCES customer (id),
    status              VARCHAR(16)   NOT NULL DEFAULT 'PENDING_PAYMENT'
                                      CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'FAILED')),
    contact_name        VARCHAR(80)   NOT NULL,
    contact_phone       VARCHAR(16)   NOT NULL,
    contact_email       VARCHAR(254),
    address_line1       VARCHAR(160)  NOT NULL,
    address_line2       VARCHAR(160),
    city                VARCHAR(80)   NOT NULL,
    state               VARCHAR(80)   NOT NULL,
    pincode             CHAR(6)       NOT NULL CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    items_rupees        NUMERIC(12, 2) NOT NULL CHECK (items_rupees >= 0),
    shipping_rupees     NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (shipping_rupees >= 0),
    total_rupees        NUMERIC(12, 2) NOT NULL CHECK (total_rupees > 0),
    razorpay_order_id   VARCHAR(40)   UNIQUE,
    razorpay_payment_id VARCHAR(40)   UNIQUE,
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (status <> 'PAID' OR (paid_at IS NOT NULL AND razorpay_payment_id IS NOT NULL))
);

CREATE INDEX web_order_customer_idx ON web_order (customer_id, created_at DESC);
CREATE INDEX web_order_created_idx ON web_order (created_at DESC);

CREATE TABLE web_order_line (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id      BIGINT        NOT NULL REFERENCES web_order (id) ON DELETE CASCADE,
    position      INT           NOT NULL,
    sku           VARCHAR(64)   NOT NULL,
    product_slug  VARCHAR(160)  NOT NULL,
    name          VARCHAR(160)  NOT NULL,
    colour_name   VARCHAR(60),
    size          VARCHAR(10),
    addon_code    VARCHAR(40),
    addon_name    VARCHAR(80),
    blouse_size   VARCHAR(10),
    quantity      INT           NOT NULL CHECK (quantity > 0),
    unit_rupees   NUMERIC(12, 2) NOT NULL CHECK (unit_rupees >= 0),
    addon_rupees  NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (addon_rupees >= 0),
    amount_rupees NUMERIC(12, 2) NOT NULL CHECK (amount_rupees >= 0),
    image_key     VARCHAR(300)
);

CREATE INDEX web_order_line_order_idx ON web_order_line (order_id, position);

CREATE SEQUENCE web_order_number_seq START 1001;
