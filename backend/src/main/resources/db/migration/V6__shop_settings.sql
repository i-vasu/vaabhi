-- Shop settings for the admin dashboard.
-- Simple settings live as one JSON document per section (shop profile, billing, documents, …) and are validated by
-- typed records in the application. Lists that other modules reference (stages, delivery packages, garment
-- templates) and the number sequences get proper tables.

CREATE TABLE shop_setting (
    section    VARCHAR(40)  PRIMARY KEY,
    value      JSONB        NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by VARCHAR(120)
);

-- Document numbers: ORD-1001, INV-1001, … The next value is taken atomically when a document is created.
CREATE TABLE number_sequence (
    kind       VARCHAR(20) PRIMARY KEY CHECK (kind IN ('ORDER', 'INVOICE', 'ALTERATION', 'ITEM')),
    prefix     VARCHAR(12) NOT NULL DEFAULT '',
    next_value BIGINT      NOT NULL DEFAULT 1 CHECK (next_value > 0),
    suffix     VARCHAR(12) NOT NULL DEFAULT '',
    -- Zero-pads the number to this many digits (item codes 001, 002, …); 0 means no padding.
    pad        INT         NOT NULL DEFAULT 0 CHECK (pad BETWEEN 0 AND 10)
);

INSERT INTO number_sequence (kind, prefix, next_value, suffix, pad) VALUES
    ('ORDER', 'ORD-', 1001, '', 0),
    ('INVOICE', 'INV-', 1001, '', 0),
    ('ALTERATION', 'ALT-', 1001, '', 0),
    ('ITEM', '', 1, '', 3);

-- Production stages every order item moves through. WORK stages are done by tailors (and can carry piece rates);
-- READY and DELIVERED close the flow and always come last, in that order.
CREATE TABLE production_stage (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name     VARCHAR(60) NOT NULL,
    kind     VARCHAR(12) NOT NULL CHECK (kind IN ('WORK', 'READY', 'DELIVERED')),
    position INT         NOT NULL,
    active   BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX production_stage_ready_uq ON production_stage (kind) WHERE kind IN ('READY', 'DELIVERED');

INSERT INTO production_stage (name, kind, position) VALUES
    ('Cutting', 'WORK', 1),
    ('Stitching', 'WORK', 2),
    ('Finishing', 'WORK', 3),
    ('Ready for pickup', 'READY', 4),
    ('Delivered', 'DELIVERED', 5);

-- Delivery plans offered on a new order, e.g. "Express (3 days)" for ₹200.
CREATE TABLE delivery_package (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(60) NOT NULL,
    price_paise BIGINT      NOT NULL DEFAULT 0 CHECK (price_paise >= 0),
    days        INT         NOT NULL CHECK (days BETWEEN 0 AND 365),
    position    INT         NOT NULL DEFAULT 0,
    active      BOOLEAN     NOT NULL DEFAULT TRUE
);

-- Measurement forms per garment. fields: [{"key":"bust","label":"Bust","unit":"in"}, …]
CREATE TABLE garment_template (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(60) NOT NULL,
    business_type VARCHAR(20) NOT NULL DEFAULT 'ALL'
        CHECK (business_type IN ('ALL', 'LADIES', 'GENTS', 'KIDS')),
    fields        JSONB       NOT NULL DEFAULT '[]',
    position      INT         NOT NULL DEFAULT 0,
    active        BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX garment_template_name_uq ON garment_template (lower(name));

INSERT INTO garment_template (name, business_type, position, fields) VALUES
    ('Blouse', 'LADIES', 1, '[
        {"key":"blouse_length","label":"Blouse length","unit":"in"},
        {"key":"bust","label":"Bust","unit":"in"},
        {"key":"under_bust","label":"Under bust","unit":"in"},
        {"key":"waist","label":"Waist","unit":"in"},
        {"key":"shoulder","label":"Shoulder","unit":"in"},
        {"key":"armhole","label":"Armhole","unit":"in"},
        {"key":"sleeve_length","label":"Sleeve length","unit":"in"},
        {"key":"sleeve_round","label":"Sleeve round","unit":"in"},
        {"key":"front_neck","label":"Front neck depth","unit":"in"},
        {"key":"back_neck","label":"Back neck depth","unit":"in"}
    ]'),
    ('Chudidar / Kurti', 'LADIES', 2, '[
        {"key":"top_length","label":"Top length","unit":"in"},
        {"key":"bust","label":"Bust","unit":"in"},
        {"key":"waist","label":"Waist","unit":"in"},
        {"key":"hip","label":"Hip","unit":"in"},
        {"key":"shoulder","label":"Shoulder","unit":"in"},
        {"key":"sleeve_length","label":"Sleeve length","unit":"in"},
        {"key":"bottom_length","label":"Bottom length","unit":"in"},
        {"key":"ankle","label":"Ankle","unit":"in"}
    ]'),
    ('Lehenga', 'LADIES', 3, '[
        {"key":"waist","label":"Waist","unit":"in"},
        {"key":"hip","label":"Hip","unit":"in"},
        {"key":"length","label":"Skirt length","unit":"in"}
    ]'),
    ('Gents shirt', 'GENTS', 4, '[
        {"key":"length","label":"Shirt length","unit":"in"},
        {"key":"chest","label":"Chest","unit":"in"},
        {"key":"waist","label":"Waist","unit":"in"},
        {"key":"shoulder","label":"Shoulder","unit":"in"},
        {"key":"sleeve_length","label":"Sleeve length","unit":"in"},
        {"key":"collar","label":"Collar","unit":"in"}
    ]'),
    ('Gents pant', 'GENTS', 5, '[
        {"key":"length","label":"Pant length","unit":"in"},
        {"key":"waist","label":"Waist","unit":"in"},
        {"key":"hip","label":"Seat","unit":"in"},
        {"key":"thigh","label":"Thigh","unit":"in"},
        {"key":"bottom","label":"Bottom","unit":"in"}
    ]'),
    ('Kids frock', 'KIDS', 6, '[
        {"key":"length","label":"Frock length","unit":"in"},
        {"key":"chest","label":"Chest","unit":"in"},
        {"key":"waist","label":"Waist","unit":"in"},
        {"key":"shoulder","label":"Shoulder","unit":"in"}
    ]');
