-- Money: invoices (raised when an order is delivered, or by hand), expenses, and alteration payments as dated rows
-- so the financial ledger can list every rupee in and out. Money is in paise.

-- Money taken for an alteration (until now only a running total on the alteration). Written by the tailoring
-- module; the ledger reads it alongside order_payment and expense.
CREATE TABLE alteration_payment (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alteration_id BIGINT       NOT NULL REFERENCES alteration (id) ON DELETE CASCADE,
    amount_paise  BIGINT       NOT NULL CHECK (amount_paise > 0),
    mode          VARCHAR(8)   NOT NULL CHECK (mode IN ('CASH', 'UPI', 'CARD', 'BANK')),
    received_by   VARCHAR(120),
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX alteration_payment_received ON alteration_payment (received_at);

-- Earlier collections become one cash row each, dated when the alteration was delivered (or booked).
INSERT INTO alteration_payment (alteration_id, amount_paise, mode, received_at)
SELECT id, collected_paise, 'CASH', COALESCE(delivered_at, created_at) FROM alteration WHERE collected_paise > 0;

-- A tax invoice for an order. Amounts are frozen when it is issued; a cancelled invoice keeps its number and a new
-- one can be issued for the same order.
CREATE TABLE invoice (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number              VARCHAR(40)   NOT NULL UNIQUE,
    order_id            BIGINT        NOT NULL REFERENCES shop_order (id),
    client_id           BIGINT        NOT NULL REFERENCES client (id),
    issued_on           DATE          NOT NULL,
    -- What the order charged: items + delivery − discount.
    order_total_paise   BIGINT        NOT NULL CHECK (order_total_paise >= 0),
    -- GST: rate as charged, taxable value, and the CGST / SGST halves (intra-state sale at the counter).
    gst_rate            NUMERIC(5, 2) NOT NULL DEFAULT 0,
    prices_include_tax  BOOLEAN       NOT NULL,
    taxable_paise       BIGINT        NOT NULL CHECK (taxable_paise >= 0),
    cgst_paise          BIGINT        NOT NULL DEFAULT 0 CHECK (cgst_paise >= 0),
    sgst_paise          BIGINT        NOT NULL DEFAULT 0 CHECK (sgst_paise >= 0),
    -- What the customer pays in all: equals the order total when prices include tax, else order total + GST.
    total_paise         BIGINT        NOT NULL CHECK (total_paise >= 0),
    shop_gstin          VARCHAR(15),
    client_gstin        VARCHAR(15),
    created_by          VARCHAR(120),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cancelled_at        TIMESTAMPTZ,
    cancelled_by        VARCHAR(120)
);

-- One live invoice per order.
CREATE UNIQUE INDEX invoice_order_live ON invoice (order_id) WHERE cancelled_at IS NULL;
CREATE INDEX invoice_issued ON invoice (issued_on DESC, id DESC);

CREATE TABLE invoice_line (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id   BIGINT       NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    position     INT          NOT NULL,
    description  VARCHAR(160) NOT NULL,
    quantity     INT          NOT NULL CHECK (quantity >= 1),
    unit_paise   BIGINT       NOT NULL CHECK (unit_paise >= 0),
    amount_paise BIGINT       NOT NULL CHECK (amount_paise >= 0)
);

CREATE INDEX invoice_line_invoice ON invoice_line (invoice_id, position);

-- Money going out of the shop.
CREATE TABLE expense (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    spent_on     DATE         NOT NULL,
    category     VARCHAR(16)  NOT NULL CHECK (category IN ('RENT', 'SALARY', 'MATERIAL', 'UTILITIES', 'TRANSPORT',
                                                           'MAINTENANCE', 'MARKETING', 'OTHER')),
    description  VARCHAR(200) NOT NULL,
    amount_paise BIGINT       NOT NULL CHECK (amount_paise > 0),
    mode         VARCHAR(8)   NOT NULL CHECK (mode IN ('CASH', 'UPI', 'CARD', 'BANK')),
    paid_to      VARCHAR(120),
    created_by   VARCHAR(120),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX expense_spent ON expense (spent_on DESC, id DESC);
