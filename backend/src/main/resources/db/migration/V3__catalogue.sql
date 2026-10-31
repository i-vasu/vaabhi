-- Catalogue: products with colour × size variants, facets (occasion, fabric, weave, work), media stored in
-- Cloudflare R2 (only object keys live here), blouse add-ons and curated collections.
-- Money is stored in paise (integer), never as floating point.

ALTER TABLE category ADD COLUMN description VARCHAR(500);

-- Filterable attributes. Values are reference data managed by the admin.
CREATE TABLE facet (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(40) NOT NULL UNIQUE,
    name       VARCHAR(80) NOT NULL,
    position   INT         NOT NULL DEFAULT 0,
    filterable BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE facet_value (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facet_id BIGINT      NOT NULL REFERENCES facet (id) ON DELETE CASCADE,
    code     VARCHAR(60) NOT NULL,
    name     VARCHAR(80) NOT NULL,
    position INT         NOT NULL DEFAULT 0,
    UNIQUE (facet_id, code)
);

-- Shopper-facing colour groups for the colour filter; each variant keeps its own exact colour name and hex.
CREATE TABLE colour_family (
    code     VARCHAR(30) PRIMARY KEY,
    name     VARCHAR(60) NOT NULL,
    hex      CHAR(7)     NOT NULL CHECK (hex ~ '^#[0-9A-F]{6}$'),
    position INT         NOT NULL DEFAULT 0
);

CREATE TABLE product (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug              VARCHAR(160) NOT NULL UNIQUE,
    name              VARCHAR(160) NOT NULL,
    subtitle          VARCHAR(200),
    description       TEXT,
    category_id       BIGINT       NOT NULL REFERENCES category (id),
    status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    badge             VARCHAR(16) CHECK (badge IN ('NEW', 'BESTSELLER', 'BRIDAL', 'HANDLOOM')),
    care_instructions TEXT,
    saree_length_m    NUMERIC(3, 1),
    blouse_included   BOOLEAN      NOT NULL DEFAULT FALSE,
    blouse_length_m   NUMERIC(3, 1),
    popularity        INT          NOT NULL DEFAULT 0,
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX product_category_status_idx ON product (category_id, status);

CREATE TABLE product_variant (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id    BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    sku           VARCHAR(64) NOT NULL UNIQUE,
    colour_name   VARCHAR(60) NOT NULL,
    colour_hex    CHAR(7)     NOT NULL CHECK (colour_hex ~ '^#[0-9A-F]{6}$'),
    colour_family VARCHAR(30) NOT NULL REFERENCES colour_family (code),
    -- NULL means one size (sarees, dupattas).
    size          VARCHAR(10),
    price_paise   INT         NOT NULL CHECK (price_paise > 0),
    mrp_paise     INT         NOT NULL,
    stock_qty     INT         NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    position      INT         NOT NULL DEFAULT 0,
    CHECK (mrp_paise >= price_paise),
    UNIQUE NULLS NOT DISTINCT (product_id, colour_name, size)
);

CREATE INDEX product_variant_product_idx ON product_variant (product_id) WHERE active;

CREATE TABLE product_facet_value (
    product_id     BIGINT NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    facet_value_id BIGINT NOT NULL REFERENCES facet_value (id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, facet_value_id)
);

CREATE INDEX product_facet_value_value_idx ON product_facet_value (facet_value_id);

-- Photos and videos live in R2; storage_key is the object key prefix (renditions are derived from it).
CREATE TABLE product_media (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    kind        VARCHAR(8)   NOT NULL DEFAULT 'IMAGE' CHECK (kind IN ('IMAGE', 'VIDEO')),
    storage_key VARCHAR(300) NOT NULL UNIQUE,
    colour_name VARCHAR(60),
    alt_text    VARCHAR(200) NOT NULL,
    width       INT,
    height      INT,
    position    INT          NOT NULL DEFAULT 0,
    status      VARCHAR(8)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'READY')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX product_media_product_idx ON product_media (product_id, position);

-- Optional extras chosen with a product, e.g. blouse stitching for a saree.
-- input: NONE (nothing to ask), SIZE (a standard blouse size), MEASUREMENTS (custom measurements).
CREATE TABLE product_addon (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    kind        VARCHAR(16)  NOT NULL DEFAULT 'BLOUSE' CHECK (kind IN ('BLOUSE')),
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(300),
    price_paise INT          NOT NULL DEFAULT 0 CHECK (price_paise >= 0),
    input       VARCHAR(16)  NOT NULL DEFAULT 'NONE' CHECK (input IN ('NONE', 'SIZE', 'MEASUREMENTS')),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    position    INT          NOT NULL DEFAULT 0,
    UNIQUE (product_id, code)
);

CREATE TABLE collection (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug        VARCHAR(120) NOT NULL UNIQUE,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    position    INT          NOT NULL DEFAULT 0
);

CREATE TABLE collection_product (
    collection_id BIGINT NOT NULL REFERENCES collection (id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    position      INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (collection_id, product_id)
);

-- Reference data (real, not demo): category descriptions, facets and colour families.
UPDATE category SET description = CASE slug
    WHEN 'sarees' THEN 'Handloom silks, Banarasi and everyday cotton sarees.'
    WHEN 'lehengas' THEN 'Bridal and festive lehengas with blouse and dupatta.'
    WHEN 'kurta-sets' THEN 'Kurta, pant and dupatta sets for every day and every festival.'
    WHEN 'salwar-suits' THEN 'Stitched and semi-stitched salwar suits.'
    WHEN 'dupattas' THEN 'Silk, chiffon and handblock dupattas.'
    ELSE description END;

INSERT INTO facet (code, name, position) VALUES
    ('fabric', 'Fabric', 1),
    ('occasion', 'Occasion', 2),
    ('weave', 'Weave', 3),
    ('work', 'Work', 4);

INSERT INTO facet_value (facet_id, code, name, position)
SELECT f.id, v.code, v.name, v.position
FROM facet f
JOIN (VALUES
    ('fabric', 'silk', 'Silk', 1),
    ('fabric', 'cotton', 'Cotton', 2),
    ('fabric', 'georgette', 'Georgette', 3),
    ('fabric', 'chiffon', 'Chiffon', 4),
    ('fabric', 'organza', 'Organza', 5),
    ('fabric', 'linen', 'Linen', 6),
    ('fabric', 'velvet', 'Velvet', 7),
    ('fabric', 'crepe', 'Crepe', 8),
    ('occasion', 'wedding', 'Wedding', 1),
    ('occasion', 'festive', 'Festive', 2),
    ('occasion', 'party', 'Party', 3),
    ('occasion', 'office', 'Office', 4),
    ('occasion', 'casual', 'Casual', 5),
    ('weave', 'banarasi', 'Banarasi', 1),
    ('weave', 'kanjeevaram', 'Kanjeevaram', 2),
    ('weave', 'chanderi', 'Chanderi', 3),
    ('weave', 'paithani', 'Paithani', 4),
    ('weave', 'ikat', 'Ikat', 5),
    ('weave', 'jamdani', 'Jamdani', 6),
    ('work', 'zari', 'Zari', 1),
    ('work', 'embroidery', 'Embroidery', 2),
    ('work', 'mirror', 'Mirror work', 3),
    ('work', 'sequin', 'Sequin', 4),
    ('work', 'printed', 'Printed', 5),
    ('work', 'block-print', 'Block print', 6),
    ('work', 'hand-painted', 'Hand painted', 7)
) AS v (facet, code, name, position) ON v.facet = f.code;

INSERT INTO colour_family (code, name, hex, position) VALUES
    ('red', 'Red', '#B3261E', 1),
    ('maroon', 'Maroon', '#6E2A35', 2),
    ('pink', 'Pink', '#D8698F', 3),
    ('orange', 'Orange', '#D9772B', 4),
    ('yellow', 'Yellow', '#E3B23C', 5),
    ('green', 'Green', '#3F7D4E', 6),
    ('blue', 'Blue', '#2F5D8C', 7),
    ('purple', 'Purple', '#6B3F7D', 8),
    ('gold', 'Gold', '#A88B5A', 9),
    ('beige', 'Beige', '#E4D3B5', 10),
    ('white', 'White', '#F7F4EE', 11),
    ('black', 'Black', '#2B2622', 12),
    ('grey', 'Grey', '#8C8680', 13),
    ('multi', 'Multicolour', '#C2185B', 14);
