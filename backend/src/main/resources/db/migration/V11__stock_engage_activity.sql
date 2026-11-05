-- Stock, coupons, rewards, customer messages, galleries, the activity log and notification read markers.
-- Money is in paise; stock quantities allow decimals (metres of cloth).

-- New dashboard areas: STOCK, MARKETING (coupons, rewards, gallery) and REPORTS (reports, activity log).
ALTER TABLE role_permission DROP CONSTRAINT role_permission_permission_check;
ALTER TABLE role_permission ADD CONSTRAINT role_permission_permission_check CHECK (permission IN (
    'ORDERS', 'WORKSHOP', 'MONEY', 'STAFF', 'PAYROLL', 'CATALOGUE', 'SETTINGS', 'STOCK', 'MARKETING', 'REPORTS'));
INSERT INTO role_permission (role, permission) VALUES ('MANAGER', 'STOCK'), ('MANAGER', 'MARKETING'), ('MANAGER', 'REPORTS');

-- Things the shop keeps: cloth, lining, thread, buttons, packing.
CREATE TABLE stock_item (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(120)   NOT NULL,
    sku           VARCHAR(40) UNIQUE,
    category      VARCHAR(40),
    unit          VARCHAR(8)     NOT NULL CHECK (unit IN ('PCS', 'M', 'CM', 'KG', 'G', 'L', 'ROLL', 'BOX', 'SET')),
    -- Alert when what is on hand falls to this or below.
    reorder_level NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    -- Cost of one unit, for stock value.
    cost_paise    BIGINT         NOT NULL DEFAULT 0 CHECK (cost_paise >= 0),
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX stock_item_name_uq ON stock_item (lower(name)) WHERE active;

-- Every change to what is on hand. quantity is the signed change: IN adds, OUT takes away, ADJUST either way.
CREATE TABLE stock_movement (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id    BIGINT         NOT NULL REFERENCES stock_item (id),
    kind       VARCHAR(8)     NOT NULL CHECK (kind IN ('IN', 'OUT', 'ADJUST')),
    quantity   NUMERIC(12, 2) NOT NULL CHECK (quantity <> 0),
    order_id   BIGINT REFERENCES shop_order (id),
    note       VARCHAR(200),
    moved_by   VARCHAR(120),
    moved_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX stock_movement_item ON stock_movement (item_id, moved_at);

-- Discount codes. value is paise for AMOUNT and basis points (10 % = 1000) for PERCENT, like an order's discount.
CREATE TABLE coupon (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(40)  NOT NULL UNIQUE CHECK (code = upper(code)),
    description     VARCHAR(200),
    kind            VARCHAR(8)   NOT NULL CHECK (kind IN ('AMOUNT', 'PERCENT')),
    value           BIGINT       NOT NULL CHECK (value > 0),
    min_order_paise BIGINT       NOT NULL DEFAULT 0 CHECK (min_order_paise >= 0),
    valid_from      DATE,
    valid_to        DATE,
    max_uses        INT CHECK (max_uses > 0),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (kind <> 'PERCENT' OR value <= 10000),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);

-- Loyalty points: one row of settings, and every point earned, redeemed or adjusted.
CREATE TABLE reward_settings (
    id              SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    -- Points earned for every ₹100 of a delivered order.
    points_per_100  INT     NOT NULL DEFAULT 1 CHECK (points_per_100 BETWEEN 0 AND 100),
    -- What one point is worth when redeemed.
    paise_per_point INT     NOT NULL DEFAULT 100 CHECK (paise_per_point BETWEEN 0 AND 10000)
);

INSERT INTO reward_settings (id) VALUES (1);

CREATE TABLE reward_entry (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id  BIGINT       NOT NULL REFERENCES client (id) ON DELETE CASCADE,
    kind       VARCHAR(8)   NOT NULL CHECK (kind IN ('EARNED', 'REDEEMED', 'ADJUSTED')),
    points     INT          NOT NULL CHECK (points <> 0),
    order_id   BIGINT REFERENCES shop_order (id),
    note       VARCHAR(200),
    created_by VARCHAR(120),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX reward_entry_client ON reward_entry (client_id, created_at);
-- An order earns points once.
CREATE UNIQUE INDEX reward_entry_earned_once ON reward_entry (order_id) WHERE kind = 'EARNED';

-- Every WhatsApp / SMS prepared or sent to a customer (review requests are purpose REVIEW).
CREATE TABLE message_log (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    channel       VARCHAR(8)    NOT NULL CHECK (channel IN ('WHATSAPP', 'SMS')),
    purpose       VARCHAR(16)   NOT NULL CHECK (purpose IN ('ORDER_PLACED', 'ORDER_READY', 'ORDER_DELIVERED',
                                                            'PAYMENT', 'REVIEW', 'REWARD', 'CUSTOM')),
    client_id     BIGINT REFERENCES client (id) ON DELETE SET NULL,
    order_id      BIGINT REFERENCES shop_order (id) ON DELETE SET NULL,
    to_phone      VARCHAR(16)   NOT NULL,
    body          VARCHAR(2000) NOT NULL,
    -- LINK: a wa.me / sms: link was made for staff to send from their phone; SENT / FAILED: through a provider.
    status        VARCHAR(8)    NOT NULL CHECK (status IN ('LINK', 'SENT', 'FAILED')),
    provider_ref  VARCHAR(120),
    error         VARCHAR(300),
    created_by    VARCHAR(120),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX message_log_client ON message_log (client_id, created_at DESC);
CREATE INDEX message_log_created ON message_log (created_at DESC);

-- Design ideas and finished garments.
CREATE TABLE gallery_photo (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind        VARCHAR(8)   NOT NULL CHECK (kind IN ('DESIGN', 'CUSTOMER')),
    title       VARCHAR(120) NOT NULL,
    tags        VARCHAR(200),
    template_id BIGINT REFERENCES garment_template (id),
    client_id   BIGINT REFERENCES client (id) ON DELETE SET NULL,
    order_id    BIGINT REFERENCES shop_order (id) ON DELETE SET NULL,
    photo_key   VARCHAR(200) NOT NULL,
    created_by  VARCHAR(120),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX gallery_photo_kind ON gallery_photo (kind, created_at DESC);

-- What happened in the shop, newest first; also the notifications feed.
CREATE TABLE activity_log (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    kind     VARCHAR(30)  NOT NULL,
    title    VARCHAR(200) NOT NULL,
    actor    VARCHAR(120),
    -- What it is about, for a link: ORDER, ALTERATION, CLIENT, SETTINGS.
    ref_type VARCHAR(20),
    ref_id   BIGINT
);

CREATE INDEX activity_log_at ON activity_log (at DESC);

-- When each person last looked at their notifications.
CREATE TABLE notification_seen (
    staff_id BIGINT      PRIMARY KEY REFERENCES staff_account (id) ON DELETE CASCADE,
    seen_at  TIMESTAMPTZ NOT NULL
);
