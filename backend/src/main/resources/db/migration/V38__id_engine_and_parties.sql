-- Phase 2 of the books plan: one numbering engine for every document and code, and one list of parties
-- (customers, suppliers, job workers) that later phases hang payments and ledger entries on.

-- --- ID engine -----------------------------------------------------------------------------------------------------
-- A sequence can restart every financial year (April–March): {FY} in the prefix or suffix prints the year, e.g.
-- INV/{FY}/ → INV/26-27/0001. period is the year the next value belongs to; a new year starts again at 1.
ALTER TABLE number_sequence DROP CONSTRAINT IF EXISTS number_sequence_kind_check;
ALTER TABLE number_sequence
    ALTER COLUMN prefix TYPE VARCHAR(20),
    ALTER COLUMN suffix TYPE VARCHAR(20),
    ADD COLUMN reset  VARCHAR(8) NOT NULL DEFAULT 'NEVER' CHECK (reset IN ('NEVER', 'FY')),
    ADD COLUMN period VARCHAR(8) NOT NULL DEFAULT '';

-- Invoices: a new series per financial year, as GST invoices must be unique within a year (at most 16 characters).
-- The old INV-1001 series stays on the invoices already issued.
UPDATE number_sequence SET prefix = 'INV/{FY}/', next_value = 1, pad = 4, reset = 'FY' WHERE kind = 'INVOICE';

-- Web orders move off their own Postgres sequence and carry on from where it stopped.
INSERT INTO number_sequence (kind, prefix, next_value, suffix, pad)
SELECT 'WEB_ORDER', 'WEB-', CASE WHEN is_called THEN last_value + 1 ELSE last_value END, '', 0
FROM web_order_number_seq;

INSERT INTO number_sequence (kind, prefix, next_value, suffix, pad, reset) VALUES
    ('CREDIT_NOTE', 'CN/{FY}/', 1, '', 4, 'FY'),
    ('STOCK_ITEM', 'STK-', 1, '', 4, 'NEVER');

-- Products get a style code (VB0001…); variant SKUs are built from it (VB0001-RED-M). Codes carry no meaning
-- that can change later (not the category, not the price).
ALTER TABLE product ADD COLUMN code VARCHAR(20) UNIQUE;
UPDATE product p SET code = 'VB' || lpad(x.n::text, 4, '0')
FROM (SELECT id, row_number() OVER (ORDER BY id) AS n FROM product) x
WHERE x.id = p.id;
INSERT INTO number_sequence (kind, prefix, next_value, suffix, pad)
SELECT 'PRODUCT', 'VB', COUNT(*) + 1, '', 4 FROM product;

-- --- Parties ---------------------------------------------------------------------------------------------------------
-- One record per person or business the shop deals with. A shop client and a website customer with the same phone
-- are the same party. Roles say how the shop deals with them.
CREATE TABLE party (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(160) NOT NULL,
    -- E.164 when known (+91…); one party per phone.
    phone         VARCHAR(16)  UNIQUE,
    email         VARCHAR(254),
    gstin         VARCHAR(15),
    pan           VARCHAR(10),
    address       VARCHAR(300),
    city          VARCHAR(80),
    state         VARCHAR(80),
    pin_code      VARCHAR(6),
    is_customer   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_supplier   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_job_worker BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Only used while this migration links rows; dropped at the end.
    seed_key      VARCHAR(40)
);
CREATE INDEX party_name_idx ON party (lower(name));

ALTER TABLE client ADD COLUMN party_id BIGINT REFERENCES party (id);
ALTER TABLE customer ADD COLUMN party_id BIGINT REFERENCES party (id);
ALTER TABLE stock_item ADD COLUMN supplier_party_id BIGINT REFERENCES party (id);

-- Shop clients (phone is always E.164 and unique).
INSERT INTO party (name, phone, email, gstin, address, city, state, pin_code, is_customer)
SELECT trim(first_name || ' ' || coalesce(last_name, '')), phone, email, gstin, address, city, state, pin_code, TRUE
FROM client;
UPDATE client c SET party_id = p.id FROM party p WHERE p.phone = c.phone;

-- Website customers with a phone: the same party as the shop client when the phone matches.
INSERT INTO party (name, phone, email, address, city, state, pin_code, is_customer)
SELECT coalesce(nullif(trim(cu.name), ''), cu.phone), cu.phone, cu.email,
       nullif(trim(concat_ws(', ', cu.address_line1, cu.address_line2)), ''), cu.city, cu.state, cu.pincode, TRUE
FROM customer cu
WHERE cu.phone IS NOT NULL AND NOT EXISTS (SELECT 1 FROM party p WHERE p.phone = cu.phone);
UPDATE customer cu SET party_id = p.id FROM party p WHERE cu.phone IS NOT NULL AND p.phone = cu.phone;

-- Website customers who signed in with Google only (no phone yet).
INSERT INTO party (name, email, is_customer, seed_key)
SELECT coalesce(nullif(trim(name), ''), email, 'Customer ' || id), email, TRUE, 'customer:' || id
FROM customer WHERE phone IS NULL;
UPDATE customer cu SET party_id = p.id FROM party p WHERE p.seed_key = 'customer:' || cu.id;

-- Suppliers typed on stock items: one party per phone, else per name. Indian numbers become +91XXXXXXXXXX (as in
-- PartyService.phone); anything else is not a usable phone and the supplier is matched by name.
CREATE FUNCTION pg_temp.e164(raw TEXT) RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
        WHEN length(regexp_replace(coalesce(raw, ''), '[^0-9]', '', 'g')) = 10
            THEN '+91' || regexp_replace(raw, '[^0-9]', '', 'g')
        WHEN length(regexp_replace(coalesce(raw, ''), '[^0-9]', '', 'g')) = 12
             AND regexp_replace(raw, '[^0-9]', '', 'g') LIKE '91%'
            THEN '+' || regexp_replace(raw, '[^0-9]', '', 'g')
    END
$$;

WITH suppliers AS (
    SELECT DISTINCT ON (key) key, name, phone
    FROM (SELECT trim(supplier_name) AS name, pg_temp.e164(supplier_phone) AS phone,
                 left(coalesce(pg_temp.e164(supplier_phone), 'name:' || lower(trim(supplier_name))), 40) AS key
          FROM stock_item WHERE trim(coalesce(supplier_name, '')) <> '') s
    ORDER BY key, name
)
INSERT INTO party (name, phone, is_supplier, seed_key)
SELECT name, phone, TRUE, key
FROM suppliers
WHERE phone IS NULL OR NOT EXISTS (SELECT 1 FROM party p WHERE p.phone = suppliers.phone);
-- A supplier whose phone already belongs to a party (a customer who also supplies) is that same party.
UPDATE party p SET is_supplier = TRUE
WHERE EXISTS (SELECT 1 FROM stock_item i WHERE p.phone = pg_temp.e164(i.supplier_phone));
UPDATE stock_item i SET supplier_party_id = p.id
FROM party p
WHERE trim(coalesce(i.supplier_name, '')) <> ''
  AND (p.phone = pg_temp.e164(i.supplier_phone)
       OR (pg_temp.e164(i.supplier_phone) IS NULL
           AND p.seed_key = left('name:' || lower(trim(i.supplier_name)), 40)));

ALTER TABLE party DROP COLUMN seed_key;
