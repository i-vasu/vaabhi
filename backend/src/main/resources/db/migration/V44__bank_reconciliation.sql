-- Phase 3 of the books plan: import bank statements and Shiprocket COD remittance reports, pull Razorpay
-- settlements, match bank lines to the ledger's Bank movements, and post what a statement shows.

-- --- Accounts ------------------------------------------------------------------------------------------------------
INSERT INTO account (code, name, type, system_key) VALUES
    ('5070', 'Payment gateway fees', 'EXPENSE', 'EXPENSE_GATEWAY_FEES'),
    ('5080', 'Courier & COD charges', 'EXPENSE', 'EXPENSE_COURIER'),
    ('5095', 'Bank charges', 'EXPENSE', 'EXPENSE_BANK_CHARGES');

-- --- Journal source types ------------------------------------------------------------------------------------------
-- V32 declared these inline, so Postgres named them journal_entry_<column>_check. Widened in place; the old values
-- stay valid.
ALTER TABLE journal_entry DROP CONSTRAINT journal_entry_source_type_check;
ALTER TABLE journal_entry ADD CONSTRAINT journal_entry_source_type_check
    CHECK (source_type IN ('INVOICE', 'CREDIT_NOTE', 'ORDER_PAYMENT', 'ALTERATION_PAYMENT', 'WEB_ORDER',
                           'WEB_RETURN', 'EXPENSE', 'MANUAL', 'OPENING', 'RAZORPAY_SETTLEMENT', 'COD_REMITTANCE',
                           'BANK_LINE'));

ALTER TABLE journal_entry DROP CONSTRAINT journal_entry_source_step_check;
ALTER TABLE journal_entry ADD CONSTRAINT journal_entry_source_step_check
    CHECK (source_step IN ('ISSUE', 'APPLY', 'CANCEL', 'RECEIPT', 'REFUND', 'EXPENSE', 'MANUAL', 'OPENING',
                           'REVERSAL', 'SETTLE', 'MATCH', 'POST'));

-- --- Import profiles -----------------------------------------------------------------------------------------------
-- The column mapping last used for a kind of file. The import picks the latest and lets the owner change it.
CREATE TABLE import_profile (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind       VARCHAR(15) NOT NULL CHECK (kind IN ('BANK', 'COD_REMITTANCE')),
    name       VARCHAR(80),
    mapping    JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX import_profile_kind ON import_profile (kind, created_at DESC, id DESC);

-- --- Bank statements -----------------------------------------------------------------------------------------------
CREATE TABLE bank_statement (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id     BIGINT         NOT NULL REFERENCES account (id),
    file_name      VARCHAR(200)   NOT NULL,
    period_from    DATE           NOT NULL,
    period_to      DATE           NOT NULL,
    opening_rupees NUMERIC(12, 2),
    closing_rupees NUMERIC(12, 2),
    line_count     INT            NOT NULL DEFAULT 0 CHECK (line_count >= 0),
    uploaded_by    VARCHAR(120),
    uploaded_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX bank_statement_uploaded ON bank_statement (uploaded_at DESC, id DESC);

-- One row per movement on the statement. dedupe_key is sha256 of the line's text, unique per account, so an
-- overlapping re-upload adds nothing.
CREATE TABLE bank_line (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    statement_id        BIGINT         NOT NULL REFERENCES bank_statement (id) ON DELETE CASCADE,
    account_id          BIGINT         NOT NULL REFERENCES account (id),
    line_date           DATE           NOT NULL,
    description         VARCHAR(300)   NOT NULL,
    reference           VARCHAR(100),
    debit_rupees        NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (debit_rupees >= 0),
    credit_rupees       NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (credit_rupees >= 0),
    balance_rupees      NUMERIC(12, 2),
    dedupe_key          VARCHAR(64)    NOT NULL,
    status              VARCHAR(9)     NOT NULL DEFAULT 'UNMATCHED'
                        CHECK (status IN ('UNMATCHED', 'MATCHED', 'POSTED', 'IGNORED')),
    ignore_reason       VARCHAR(200),
    journal_entry_id    BIGINT REFERENCES journal_entry (id),
    difference_entry_id BIGINT REFERENCES journal_entry (id),
    matched_by          VARCHAR(20),
    matched_at          TIMESTAMPTZ,
    CONSTRAINT bank_line_one_side CHECK ((debit_rupees > 0) <> (credit_rupees > 0))
);

CREATE UNIQUE INDEX bank_line_dedupe ON bank_line (account_id, dedupe_key);
CREATE INDEX bank_line_statement ON bank_line (statement_id, line_date);

-- --- Razorpay settlements ------------------------------------------------------------------------------------------
-- seq numbers the settlements for the journal's source_id; id is Razorpay's setl_ reference.
CREATE TABLE razorpay_settlement (
    id           VARCHAR(40)    PRIMARY KEY,
    seq          BIGSERIAL      NOT NULL UNIQUE,
    utr          VARCHAR(100),
    settled_on   DATE           NOT NULL,
    gross_rupees NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (gross_rupees >= 0),
    fee_rupees   NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (fee_rupees >= 0),
    tax_rupees   NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (tax_rupees >= 0),
    net_rupees   NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (net_rupees >= 0),
    synced_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX razorpay_settlement_settled ON razorpay_settlement (settled_on DESC, seq DESC);
CREATE INDEX razorpay_settlement_utr ON razorpay_settlement (utr) WHERE utr IS NOT NULL;

-- --- Shiprocket COD remittances ------------------------------------------------------------------------------------
CREATE TABLE cod_remittance (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    remittance_ref   VARCHAR(100)   NOT NULL UNIQUE,
    utr              VARCHAR(100),
    remitted_on      DATE           NOT NULL,
    cod_rupees       NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (cod_rupees >= 0),
    deduction_rupees NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (deduction_rupees >= 0),
    net_rupees       NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (net_rupees >= 0),
    file_name        VARCHAR(200),
    uploaded_by      VARCHAR(120),
    uploaded_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX cod_remittance_remitted ON cod_remittance (remitted_on DESC, id DESC);
CREATE INDEX cod_remittance_utr ON cod_remittance (utr) WHERE utr IS NOT NULL;

-- One row per AWB in a remittance report; web_order_id is set when the AWB matches a web order.
CREATE TABLE cod_remittance_line (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    remittance_id BIGINT         NOT NULL REFERENCES cod_remittance (id) ON DELETE CASCADE,
    awb           VARCHAR(40)    NOT NULL,
    order_number  VARCHAR(40),
    cod_rupees    NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (cod_rupees >= 0),
    web_order_id  BIGINT REFERENCES web_order (id)
);

CREATE INDEX cod_remittance_line_remittance ON cod_remittance_line (remittance_id);
CREATE INDEX cod_remittance_line_awb ON cod_remittance_line (awb);

-- --- Audit log -----------------------------------------------------------------------------------------------------
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['bank_line', 'cod_remittance', 'razorpay_settlement']
    LOOP
        EXECUTE format('CREATE TRIGGER audit_%1$s AFTER INSERT OR UPDATE OR DELETE ON %1$I
                        FOR EACH ROW EXECUTE FUNCTION audit_row()', t);
    END LOOP;
END;
$$;
