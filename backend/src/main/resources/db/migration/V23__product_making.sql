-- Categories: "Kurta sets" becomes "Kurtis", "Salwar suits" is dropped (its products move to Kurtis) and
-- "Short tops" is added.
UPDATE category SET slug = 'kurtis', name = 'Kurtis', description = 'Kurtis for every day and every festival.'
WHERE slug = 'kurta-sets';

UPDATE product SET category_id = (SELECT id FROM category WHERE slug = 'kurtis')
WHERE category_id = (SELECT id FROM category WHERE slug = 'salwar-suits')
  AND EXISTS (SELECT 1 FROM category WHERE slug = 'kurtis');

DELETE FROM category c
WHERE c.slug = 'salwar-suits' AND NOT EXISTS (SELECT 1 FROM product p WHERE p.category_id = c.id);

INSERT INTO category (slug, name, position, description)
SELECT 'short-tops', 'Short tops', COALESCE(MAX(position), 0) + 1, 'Short tops and crop tops to pair with anything.'
FROM category
WHERE NOT EXISTS (SELECT 1 FROM category WHERE slug = 'short-tops');

-- Stock: who sold it and what the fabric is.
ALTER TABLE stock_item
    ADD COLUMN supplier_name  VARCHAR(120),
    ADD COLUMN supplier_phone VARCHAR(20),
    ADD COLUMN material       VARCHAR(60),
    ADD COLUMN colour         VARCHAR(60),
    ADD COLUMN gsm            INT CHECK (gsm BETWEEN 1 AND 2000),
    ADD COLUMN thread_count   INT CHECK (thread_count BETWEEN 1 AND 5000),
    ADD COLUMN width_in       NUMERIC(5, 1) CHECK (width_in > 0),
    ADD COLUMN notes          VARCHAR(500);

-- Products: fabric weight and thread count (filled from the stock they are made from) and measurements that
-- depend on the category (kurti length, dupatta width…). Each dimension carries its own label and unit.
ALTER TABLE product
    ADD COLUMN gsm          INT CHECK (gsm BETWEEN 1 AND 2000),
    ADD COLUMN thread_count INT CHECK (thread_count BETWEEN 1 AND 5000);

CREATE TABLE product_dimension (
    product_id BIGINT        NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    code       VARCHAR(40)   NOT NULL,
    label      VARCHAR(60)   NOT NULL,
    unit       VARCHAR(4)    NOT NULL CHECK (unit IN ('IN', 'CM', 'M')),
    value      NUMERIC(7, 2) NOT NULL CHECK (value > 0),
    position   INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, code)
);

-- Which stock a product was made from: 10 m bought from a supplier, 2.5 m of it into this dress. Each row takes
-- the quantity out of stock through its own OUT movement, removed again when the row is.
CREATE TABLE product_material (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id    BIGINT         NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    stock_item_id BIGINT         NOT NULL REFERENCES stock_item (id),
    quantity      NUMERIC(12, 2) NOT NULL CHECK (quantity > 0),
    movement_id   BIGINT         NOT NULL REFERENCES stock_movement (id),
    note          VARCHAR(200),
    created_by    VARCHAR(120),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX product_material_product ON product_material (product_id);
CREATE INDEX product_material_stock ON product_material (stock_item_id);

-- Who did what on a product (stitching, embroidery, finishing…) and what it cost. staff_id is set when a staff
-- member did it; outside workers are recorded by name alone.
CREATE TABLE product_work (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    staff_id    BIGINT REFERENCES staff_account (id),
    worker_name VARCHAR(120) NOT NULL,
    work_type   VARCHAR(60)  NOT NULL,
    cost_rupees NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (cost_rupees >= 0),
    done_on     DATE,
    note        VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX product_work_product ON product_work (product_id);
