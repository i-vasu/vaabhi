-- Phase 1 of the books plan: a GST engine. Tax classes (HSN/SAC code and rate, with an optional price slab) hang off
-- categories, garment templates and price-list items; every invoice line records its own rate and tax; credit notes
-- reverse an invoice, whole or in part.

-- --- Tax classes ---------------------------------------------------------------------------------------------------
-- Rates here are placeholders for a chartered accountant to confirm before GST is switched on.
-- slab_limit_rupees / rate_above_percent: a piece priced above the limit is taxed at the higher rate (both or neither).
CREATE TABLE tax_class (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name               VARCHAR(60)    NOT NULL UNIQUE,
    kind               VARCHAR(7)     NOT NULL CHECK (kind IN ('GOODS', 'SERVICE')),
    hsn_code           VARCHAR(8)     NOT NULL CHECK (hsn_code ~ '^[0-9]{4,8}$'),
    rate_percent       NUMERIC(5, 2)  NOT NULL CHECK (rate_percent BETWEEN 0 AND 28),
    slab_limit_rupees  NUMERIC(12, 2) CHECK (slab_limit_rupees > 0),
    rate_above_percent NUMERIC(5, 2)  CHECK (rate_above_percent BETWEEN 0 AND 28),
    CONSTRAINT tax_class_slab_chk CHECK ((slab_limit_rupees IS NULL) = (rate_above_percent IS NULL))
);

INSERT INTO tax_class (name, kind, hsn_code, rate_percent, slab_limit_rupees, rate_above_percent) VALUES
    ('Garments', 'GOODS', '6204', 5, 2500, 18);
INSERT INTO tax_class (name, kind, hsn_code, rate_percent) VALUES
    ('Tailoring service', 'SERVICE', '998821', 5);

ALTER TABLE category ADD COLUMN tax_class_id BIGINT REFERENCES tax_class (id);
ALTER TABLE garment_template ADD COLUMN tax_class_id BIGINT REFERENCES tax_class (id);
ALTER TABLE service_product ADD COLUMN tax_class_id BIGINT REFERENCES tax_class (id);

-- The billing setting names the default class (it replaces defaultGstRate, which stays in old JSON but is not read).
INSERT INTO shop_setting (section, value)
VALUES ('billing', jsonb_build_object(
        'chargeGst', false, 'gstin', NULL, 'defaultGstRate', 5, 'pricesIncludeTax', true, 'showHsnCodes', false,
        'discountAppliesTo', 'ORDER_TOTAL', 'autoInvoice', true,
        'defaultTaxClassId', (SELECT id FROM tax_class WHERE name = 'Garments')))
ON CONFLICT (section) DO UPDATE
    SET value = jsonb_set(shop_setting.value, '{defaultTaxClassId}',
                          to_jsonb((SELECT id FROM tax_class WHERE name = 'Garments')));

-- --- Tax on invoices -----------------------------------------------------------------------------------------------
ALTER TABLE invoice
    ADD COLUMN igst_rupees   NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (igst_rupees >= 0),
    ADD COLUMN place_of_supply CHAR(2),
    ADD COLUMN supply_type   VARCHAR(5)     NOT NULL DEFAULT 'INTRA' CHECK (supply_type IN ('INTRA', 'INTER'));

-- discount_rupees is the line's share of the order discount; amount_rupees stays quantity x unit. Old invoices keep
-- discount_rupees = 0 because their taxable value already reflects any discount.
ALTER TABLE invoice_line
    ADD COLUMN hsn_code          VARCHAR(8),
    ADD COLUMN gst_rate          NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    ADD COLUMN discount_rupees   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN taxable_rupees    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN cgst_rupees       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN sgst_rupees       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN igst_rupees       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN web_order_line_id BIGINT REFERENCES web_order_line (id);

UPDATE invoice SET supply_type = 'INTRA', place_of_supply = seller_state_code;

-- Old invoices: every line takes the invoice's rate, and the invoice's taxable value, CGST and SGST are spread over
-- the lines in proportion to amount. The last line (highest position) takes the remainder so the sums match.
WITH shares AS (
    SELECT l.id, l.invoice_id, i.gst_rate, i.taxable_rupees AS inv_taxable, i.cgst_rupees AS inv_cgst,
           i.sgst_rupees AS inv_sgst,
           row_number() OVER (PARTITION BY l.invoice_id ORDER BY l.position DESC, l.id DESC) AS rn,
           CASE WHEN sum(l.amount_rupees) OVER (PARTITION BY l.invoice_id) = 0 THEN 0
                ELSE round(i.taxable_rupees * l.amount_rupees
                           / sum(l.amount_rupees) OVER (PARTITION BY l.invoice_id), 2) END AS t,
           CASE WHEN sum(l.amount_rupees) OVER (PARTITION BY l.invoice_id) = 0 THEN 0
                ELSE round(i.cgst_rupees * l.amount_rupees
                           / sum(l.amount_rupees) OVER (PARTITION BY l.invoice_id), 2) END AS c,
           CASE WHEN sum(l.amount_rupees) OVER (PARTITION BY l.invoice_id) = 0 THEN 0
                ELSE round(i.sgst_rupees * l.amount_rupees
                           / sum(l.amount_rupees) OVER (PARTITION BY l.invoice_id), 2) END AS s
    FROM invoice_line l
    JOIN invoice i ON i.id = l.invoice_id
),
final AS (
    SELECT id, gst_rate,
           CASE WHEN rn = 1 THEN inv_taxable - (sum(t) OVER (PARTITION BY invoice_id) - t) ELSE t END AS taxable,
           CASE WHEN rn = 1 THEN inv_cgst - (sum(c) OVER (PARTITION BY invoice_id) - c) ELSE c END AS cgst,
           CASE WHEN rn = 1 THEN inv_sgst - (sum(s) OVER (PARTITION BY invoice_id) - s) ELSE s END AS sgst
    FROM shares
)
UPDATE invoice_line l SET
    gst_rate = f.gst_rate,
    hsn_code = NULL,
    taxable_rupees = f.taxable,
    cgst_rupees = f.cgst,
    sgst_rupees = f.sgst,
    igst_rupees = 0
FROM final f
WHERE f.id = l.id;

-- --- Credit notes --------------------------------------------------------------------------------------------------
-- A credit note reverses an invoice, whole or in part. It carries its own frozen copy of buyer and seller, like an
-- invoice, and is numbered from the CREDIT_NOTE series (CN/26-27/0001).
CREATE TABLE credit_note (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number               VARCHAR(16)    NOT NULL UNIQUE,
    invoice_id           BIGINT         NOT NULL REFERENCES invoice (id),
    reason               VARCHAR(10)    NOT NULL CHECK (reason IN ('RETURN', 'CANCELLED', 'CORRECTION')),
    note                 VARCHAR(200),
    issued_on            DATE           NOT NULL,
    web_order_return_id  BIGINT         UNIQUE REFERENCES web_order_return (id),
    title                VARCHAR(20)    NOT NULL,
    buyer_name           VARCHAR(160)   NOT NULL,
    buyer_phone          VARCHAR(16),
    buyer_address        VARCHAR(400),
    buyer_state          VARCHAR(80),
    seller_name          VARCHAR(160)   NOT NULL,
    seller_address       VARCHAR(300),
    seller_cin           VARCHAR(21),
    seller_state         VARCHAR(80),
    seller_state_code    CHAR(2),
    place_of_supply      CHAR(2),
    supply_type          VARCHAR(5)     NOT NULL DEFAULT 'INTRA' CHECK (supply_type IN ('INTRA', 'INTER')),
    taxable_rupees       NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (taxable_rupees >= 0),
    cgst_rupees          NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (cgst_rupees >= 0),
    sgst_rupees          NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (sgst_rupees >= 0),
    igst_rupees          NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (igst_rupees >= 0),
    total_rupees         NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (total_rupees >= 0),
    created_by           VARCHAR(120),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX credit_note_invoice ON credit_note (invoice_id);
CREATE INDEX credit_note_issued ON credit_note (issued_on DESC, id DESC);

CREATE TABLE credit_note_line (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credit_note_id  BIGINT         NOT NULL REFERENCES credit_note (id) ON DELETE CASCADE,
    position        INT            NOT NULL,
    invoice_line_id BIGINT         NOT NULL REFERENCES invoice_line (id),
    description     VARCHAR(160)   NOT NULL,
    hsn_code        VARCHAR(8),
    quantity        INT            NOT NULL CHECK (quantity >= 1),
    unit_rupees     NUMERIC(12, 2) NOT NULL CHECK (unit_rupees >= 0),
    gst_rate        NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    taxable_rupees  NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (taxable_rupees >= 0),
    cgst_rupees     NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (cgst_rupees >= 0),
    sgst_rupees     NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (sgst_rupees >= 0),
    igst_rupees     NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (igst_rupees >= 0),
    amount_rupees   NUMERIC(12, 2) NOT NULL CHECK (amount_rupees >= 0)
);

CREATE INDEX credit_note_line_note ON credit_note_line (credit_note_id, position);
CREATE INDEX credit_note_line_invoice_line ON credit_note_line (invoice_line_id);
