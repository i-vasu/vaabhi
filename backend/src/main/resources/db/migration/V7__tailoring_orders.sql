-- Shop customers, their measurements, the tailoring price list, orders with items and payments, and alterations.
-- Shop customers are "clients" in the database so they never clash with online customer accounts (`customer`,
-- feat/auth); the two can be linked by phone number later. Money is in paise.

CREATE TABLE client (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- E.164, e.g. +917013468353; one client per phone number.
    phone           VARCHAR(16)  NOT NULL UNIQUE,
    first_name      VARCHAR(60)  NOT NULL,
    last_name       VARCHAR(60),
    gender          VARCHAR(8)   NOT NULL DEFAULT 'FEMALE' CHECK (gender IN ('FEMALE', 'MALE', 'OTHER')),
    photo_key       VARCHAR(200),
    email           VARCHAR(254),
    alt_phone       VARCHAR(16),
    whatsapp        VARCHAR(16),
    address         VARCHAR(300),
    city            VARCHAR(80),
    state           VARCHAR(80),
    pin_code        VARCHAR(6),
    client_type     VARCHAR(12)  NOT NULL DEFAULT 'RETAIL' CHECK (client_type IN ('RETAIL', 'WHOLESALE', 'VIP')),
    gstin           VARCHAR(15),
    preferred_style VARCHAR(1000),
    birth_date      DATE,
    occasion_name   VARCHAR(60),
    occasion_date   DATE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX client_name ON client (lower(first_name || ' ' || coalesce(last_name, '')));

-- Measurements per garment; the newest per (client, template) is used on a new order.
-- values: {"bust": "34", "waist": "28", "neck_style": "boat"}
CREATE TABLE client_measurement (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id   BIGINT       NOT NULL REFERENCES client (id) ON DELETE CASCADE,
    template_id BIGINT       NOT NULL REFERENCES garment_template (id),
    values      JSONB        NOT NULL,
    notes       VARCHAR(500),
    taken_by    VARCHAR(120),
    taken_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX client_measurement_latest ON client_measurement (client_id, template_id, taken_at DESC);

-- The tailoring price list: what the shop stitches, per garment, with what each production stage costs (piece
-- rate paid to the tailor) so profit per piece is known.
CREATE TABLE service_product (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_id BIGINT       NOT NULL REFERENCES garment_template (id),
    code        VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(80)  NOT NULL,
    price_paise BIGINT       NOT NULL CHECK (price_paise >= 0),
    photo_key   VARCHAR(200),
    -- [{"stageId": 1, "paise": 5000}, …]
    stage_costs JSONB        NOT NULL DEFAULT '[]',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO service_product (template_id, code, name, price_paise)
SELECT t.id, p.code, p.name, p.price
FROM (VALUES
          ('Blouse', 'BL-001', 'Simple blouse', 30000),
          ('Blouse', 'BL-002', '3 dart blouse', 50000),
          ('Blouse', 'BL-003', 'Princess cut blouse', 80000),
          ('Chudidar / Kurti', 'CH-001', 'Anarkali', 120000),
          ('Chudidar / Kurti', 'CH-002', 'Churidar set', 70000),
          ('Lehenga', 'LE-001', 'Lehenga skirt', 150000),
          ('Gents shirt', 'GS-001', 'Formal shirt', 60000),
          ('Gents pant', 'GP-001', 'Flat front pant', 50000),
          ('Kids frock', 'KF-001', 'Frock', 60000)) AS p (template, code, name, price)
JOIN garment_template t ON t.name = p.template;

CREATE TABLE shop_order (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number                VARCHAR(40)  NOT NULL UNIQUE,
    client_id             BIGINT       NOT NULL REFERENCES client (id),
    status                VARCHAR(12)  NOT NULL DEFAULT 'BOOKED'
        CHECK (status IN ('BOOKED', 'IN_PROGRESS', 'READY', 'DELIVERED', 'CANCELLED')),
    order_date            DATE         NOT NULL,
    delivery_date         DATE         NOT NULL,
    due_time              TIME,
    trial_date            DATE,
    delivery_package_id   BIGINT REFERENCES delivery_package (id),
    delivery_charge_paise BIGINT       NOT NULL DEFAULT 0 CHECK (delivery_charge_paise >= 0),
    items_paise           BIGINT       NOT NULL CHECK (items_paise >= 0),
    discount_kind         VARCHAR(8)   NOT NULL DEFAULT 'AMOUNT' CHECK (discount_kind IN ('AMOUNT', 'PERCENT')),
    -- Paise for AMOUNT, basis points (1 % = 100) for PERCENT.
    discount_value        BIGINT       NOT NULL DEFAULT 0 CHECK (discount_value >= 0),
    discount_paise        BIGINT       NOT NULL DEFAULT 0 CHECK (discount_paise >= 0),
    coupon_code           VARCHAR(40),
    total_paise           BIGINT       NOT NULL CHECK (total_paise >= 0),
    paid_paise            BIGINT       NOT NULL DEFAULT 0,
    notes                 VARCHAR(2000),
    created_by            VARCHAR(120),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    delivered_at          TIMESTAMPTZ
);

CREATE INDEX shop_order_client ON shop_order (client_id, order_date DESC);
CREATE INDEX shop_order_delivery ON shop_order (status, delivery_date);
CREATE INDEX shop_order_date ON shop_order (order_date DESC);

CREATE TABLE order_item (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id           BIGINT       NOT NULL REFERENCES shop_order (id) ON DELETE CASCADE,
    position           INT          NOT NULL,
    template_id        BIGINT REFERENCES garment_template (id),
    service_product_id BIGINT REFERENCES service_product (id),
    description        VARCHAR(120) NOT NULL,
    quantity           INT          NOT NULL CHECK (quantity BETWEEN 1 AND 999),
    price_paise        BIGINT       NOT NULL CHECK (price_paise >= 0),
    colour             VARCHAR(60),
    note               VARCHAR(500),
    -- Copy of the measurements used, so later edits to the customer's measurements do not change this order.
    measurements       JSONB,
    cloth_photo_key    VARCHAR(200),
    stage_id           BIGINT REFERENCES production_stage (id),
    stage_changed_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX order_item_order ON order_item (order_id, position);
CREATE INDEX order_item_stage ON order_item (stage_id);

CREATE TABLE order_payment (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT       NOT NULL REFERENCES shop_order (id) ON DELETE CASCADE,
    amount_paise BIGINT      NOT NULL CHECK (amount_paise <> 0),
    mode        VARCHAR(8)   NOT NULL CHECK (mode IN ('CASH', 'UPI', 'CARD', 'BANK')),
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('ADVANCE', 'PAYMENT', 'REFUND')),
    note        VARCHAR(200),
    received_by VARCHAR(120),
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX order_payment_order ON order_payment (order_id);
CREATE INDEX order_payment_received ON order_payment (received_at);

CREATE TABLE alteration (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number                VARCHAR(40)  NOT NULL UNIQUE,
    client_id             BIGINT       NOT NULL REFERENCES client (id),
    order_id              BIGINT REFERENCES shop_order (id),
    status                VARCHAR(12)  NOT NULL DEFAULT 'BOOKED'
        CHECK (status IN ('BOOKED', 'IN_PROGRESS', 'READY', 'DELIVERED', 'CANCELLED')),
    ready_by              DATE         NOT NULL,
    paid                  BOOLEAN      NOT NULL,
    total_paise           BIGINT       NOT NULL DEFAULT 0 CHECK (total_paise >= 0),
    collected_paise       BIGINT       NOT NULL DEFAULT 0 CHECK (collected_paise >= 0),
    notes                 VARCHAR(1000),
    undertaking_accepted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by            VARCHAR(120),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    delivered_at          TIMESTAMPTZ
);

CREATE INDEX alteration_status ON alteration (status, ready_by);

CREATE TABLE alteration_item (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alteration_id  BIGINT       NOT NULL REFERENCES alteration (id) ON DELETE CASCADE,
    position       INT          NOT NULL,
    kind           VARCHAR(16)  NOT NULL CHECK (kind IN ('FIT_ADJUSTMENT', 'LENGTH', 'REPAIR', 'REWORK')),
    garment        VARCHAR(200) NOT NULL,
    instructions   VARCHAR(500),
    photo_key      VARCHAR(200),
    cost_paise     BIGINT       NOT NULL DEFAULT 0 CHECK (cost_paise >= 0)
);
