-- Phase 4 of the books plan: supplier bills with input GST and TDS, supplier payments, TDS challans, a fixed-asset
-- register that depreciates monthly, and the accounts and seeds those need.

-- --- Accounts ------------------------------------------------------------------------------------------------------
INSERT INTO account (code, name, type, system_key) VALUES
    ('5100', 'Purchases', 'EXPENSE', 'PURCHASES'),
    ('5200', 'Depreciation', 'EXPENSE', 'DEPRECIATION'),
    ('4900', 'Gain on asset disposal', 'INCOME', 'ASSET_GAIN'),
    ('5210', 'Loss on asset disposal', 'EXPENSE', 'ASSET_LOSS'),
    ('1500', 'Plant & machinery', 'ASSET', 'ASSET_PLANT'),
    ('1501', 'Acc. depreciation – plant & machinery', 'ASSET', 'ACCDEP_PLANT'),
    ('1510', 'Furniture & fittings', 'ASSET', 'ASSET_FURNITURE'),
    ('1511', 'Acc. depreciation – furniture & fittings', 'ASSET', 'ACCDEP_FURNITURE'),
    ('1520', 'Office equipment', 'ASSET', 'ASSET_OFFICE'),
    ('1521', 'Acc. depreciation – office equipment', 'ASSET', 'ACCDEP_OFFICE'),
    ('1530', 'Computers', 'ASSET', 'ASSET_COMPUTERS'),
    ('1531', 'Acc. depreciation – computers', 'ASSET', 'ACCDEP_COMPUTERS');

-- --- Journal source types ------------------------------------------------------------------------------------------
-- source_type is already VARCHAR(20); the longest new value, ASSET_DEPRECIATION, fits.
ALTER TABLE journal_entry DROP CONSTRAINT journal_entry_source_type_check;
ALTER TABLE journal_entry ADD CONSTRAINT journal_entry_source_type_check
    CHECK (source_type IN ('INVOICE', 'CREDIT_NOTE', 'ORDER_PAYMENT', 'ALTERATION_PAYMENT', 'WEB_ORDER',
                           'WEB_RETURN', 'EXPENSE', 'MANUAL', 'OPENING', 'RAZORPAY_SETTLEMENT', 'COD_REMITTANCE',
                           'BANK_LINE', 'SUPPLIER_BILL', 'SUPPLIER_PAYMENT', 'TDS_CHALLAN', 'ASSET',
                           'ASSET_DEPRECIATION'));

-- V32 declared source_step as VARCHAR(10); DEPRECIATION needs 12.
ALTER TABLE journal_entry ALTER COLUMN source_step TYPE VARCHAR(15);
ALTER TABLE journal_entry DROP CONSTRAINT journal_entry_source_step_check;
ALTER TABLE journal_entry ADD CONSTRAINT journal_entry_source_step_check
    CHECK (source_step IN ('ISSUE', 'APPLY', 'CANCEL', 'RECEIPT', 'REFUND', 'EXPENSE', 'MANUAL', 'OPENING',
                           'REVERSAL', 'SETTLE', 'MATCH', 'POST', 'PAYMENT', 'DEPRECIATION', 'DISPOSAL'));

ALTER TABLE posting_problem ALTER COLUMN source_step TYPE VARCHAR(15);

-- --- Import profiles -----------------------------------------------------------------------------------------------
-- V35 declared kind inline, so Postgres named the constraint import_profile_kind_check.
ALTER TABLE import_profile ALTER COLUMN kind TYPE VARCHAR(20);
ALTER TABLE import_profile DROP CONSTRAINT import_profile_kind_check;
ALTER TABLE import_profile ADD CONSTRAINT import_profile_kind_check
    CHECK (kind IN ('BANK', 'COD_REMITTANCE', 'SHIPROCKET_INVOICE'));

-- --- TDS sections --------------------------------------------------------------------------------------------------
-- Placeholder rates and thresholds until the CA confirms the shop's sections.
CREATE TABLE tds_section (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                    VARCHAR(10)   NOT NULL UNIQUE,
    name                    VARCHAR(80)   NOT NULL,
    rate_percent            NUMERIC(5, 2) NOT NULL CHECK (rate_percent >= 0),
    no_pan_rate_percent     NUMERIC(5, 2) NOT NULL CHECK (no_pan_rate_percent >= 0),
    year_threshold_rupees   NUMERIC(12, 2),
    single_threshold_rupees NUMERIC(12, 2),
    active                  BOOLEAN       NOT NULL DEFAULT TRUE
);

INSERT INTO tds_section (code, name, rate_percent, no_pan_rate_percent, year_threshold_rupees,
                         single_threshold_rupees) VALUES
    ('194C-I', 'Contractor (individual/HUF)', 1, 20, 100000, 30000),
    ('194C-O', 'Contractor (others)', 2, 20, 100000, 30000),
    ('194I-B', 'Rent of land or building', 10, 20, 240000, NULL),
    ('194I-A', 'Rent of plant or machinery', 2, 20, 240000, NULL),
    ('194J', 'Professional fees', 10, 20, 30000, NULL),
    ('194H', 'Commission', 2, 20, 15000, NULL);

ALTER TABLE party ADD COLUMN tds_section_id BIGINT REFERENCES tds_section (id);

-- --- Asset classes -------------------------------------------------------------------------------------------------
CREATE TABLE asset_class (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name             VARCHAR(60)   NOT NULL UNIQUE,
    life_years       INT           NOT NULL CHECK (life_years BETWEEN 1 AND 60),
    residual_percent NUMERIC(5, 2) NOT NULL DEFAULT 5 CHECK (residual_percent BETWEEN 0 AND 50),
    asset_account_id BIGINT        NOT NULL REFERENCES account (id),
    accdep_account_id BIGINT       NOT NULL REFERENCES account (id),
    active           BOOLEAN       NOT NULL DEFAULT TRUE
);

INSERT INTO asset_class (name, life_years, asset_account_id, accdep_account_id) VALUES
    ('Plant & machinery', 15,
     (SELECT id FROM account WHERE system_key = 'ASSET_PLANT'),
     (SELECT id FROM account WHERE system_key = 'ACCDEP_PLANT')),
    ('Furniture & fittings', 10,
     (SELECT id FROM account WHERE system_key = 'ASSET_FURNITURE'),
     (SELECT id FROM account WHERE system_key = 'ACCDEP_FURNITURE')),
    ('Office equipment', 5,
     (SELECT id FROM account WHERE system_key = 'ASSET_OFFICE'),
     (SELECT id FROM account WHERE system_key = 'ACCDEP_OFFICE')),
    ('Computers', 3,
     (SELECT id FROM account WHERE system_key = 'ASSET_COMPUTERS'),
     (SELECT id FROM account WHERE system_key = 'ACCDEP_COMPUTERS'));

-- --- Supplier bills ------------------------------------------------------------------------------------------------
CREATE TABLE supplier_bill (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    party_id          BIGINT         NOT NULL REFERENCES party (id),
    bill_no           VARCHAR(40)    NOT NULL,
    bill_date         DATE           NOT NULL,
    due_date          DATE,
    supply_state_code VARCHAR(2),
    taxable_rupees    NUMERIC(12, 2) NOT NULL CHECK (taxable_rupees >= 0),
    tax_rupees        NUMERIC(12, 2) NOT NULL CHECK (tax_rupees >= 0),
    total_rupees      NUMERIC(12, 2) NOT NULL CHECK (total_rupees >= 0),
    tds_section_id    BIGINT REFERENCES tds_section (id),
    tds_rupees        NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (tds_rupees >= 0),
    payable_rupees    NUMERIC(12, 2) NOT NULL,
    source            VARCHAR(12)    NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL', 'SHIPROCKET')),
    attachment_key    VARCHAR(200),
    notes             VARCHAR(300),
    status            VARCHAR(10)    NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CANCELLED')),
    cancelled_on      DATE,
    cancel_reason     VARCHAR(200),
    created_by        VARCHAR(120),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT supplier_bill_payable CHECK (payable_rupees = total_rupees - tds_rupees),
    CONSTRAINT supplier_bill_total CHECK (total_rupees = taxable_rupees + tax_rupees),
    CONSTRAINT supplier_bill_cancelled CHECK ((status = 'CANCELLED') = (cancelled_on IS NOT NULL))
);

-- A supplier's bill number is unique per supplier, ignoring case.
CREATE UNIQUE INDEX supplier_bill_no ON supplier_bill (party_id, lower(bill_no));
CREATE INDEX supplier_bill_date ON supplier_bill (bill_date DESC, id DESC);
CREATE INDEX supplier_bill_party ON supplier_bill (party_id, bill_date);

CREATE TABLE supplier_bill_line (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bill_id          BIGINT         NOT NULL REFERENCES supplier_bill (id) ON DELETE CASCADE,
    position         INT            NOT NULL,
    kind             VARCHAR(8)     NOT NULL CHECK (kind IN ('EXPENSE', 'STOCK', 'ASSET')),
    description      VARCHAR(200)   NOT NULL,
    account_id       BIGINT         NOT NULL REFERENCES account (id),
    stock_item_id    BIGINT REFERENCES stock_item (id),
    quantity         NUMERIC(12, 3),
    asset_class_id   BIGINT REFERENCES asset_class (id),
    hsn              VARCHAR(8),
    taxable_rupees   NUMERIC(12, 2) NOT NULL CHECK (taxable_rupees > 0),
    gst_rate_percent NUMERIC(5, 2)  NOT NULL CHECK (gst_rate_percent BETWEEN 0 AND 28),
    cgst_rupees      NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (cgst_rupees >= 0),
    sgst_rupees      NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (sgst_rupees >= 0),
    igst_rupees      NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (igst_rupees >= 0),
    total_rupees     NUMERIC(12, 2) NOT NULL,
    CONSTRAINT supplier_bill_line_stock
        CHECK (kind <> 'STOCK' OR (stock_item_id IS NOT NULL AND quantity > 0)),
    CONSTRAINT supplier_bill_line_asset
        CHECK (kind <> 'ASSET' OR asset_class_id IS NOT NULL),
    CONSTRAINT supplier_bill_line_total
        CHECK (total_rupees = taxable_rupees + cgst_rupees + sgst_rupees + igst_rupees),
    CONSTRAINT supplier_bill_line_position UNIQUE (bill_id, position)
);

-- --- Supplier payments ---------------------------------------------------------------------------------------------
CREATE TABLE supplier_payment (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    party_id     BIGINT         NOT NULL REFERENCES party (id),
    paid_on      DATE           NOT NULL,
    mode         VARCHAR(8)     NOT NULL CHECK (mode IN ('CASH', 'UPI', 'CARD', 'BANK')),
    amount_rupees NUMERIC(12, 2) NOT NULL CHECK (amount_rupees > 0),
    reference    VARCHAR(100),
    notes        VARCHAR(300),
    bank_line_id BIGINT UNIQUE REFERENCES bank_line (id),
    created_by   VARCHAR(120),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX supplier_payment_party ON supplier_payment (party_id, paid_on);

-- The unallocated part of a payment is an advance to the supplier.
CREATE TABLE supplier_payment_allocation (
    payment_id   BIGINT         NOT NULL REFERENCES supplier_payment (id) ON DELETE CASCADE,
    bill_id      BIGINT         NOT NULL REFERENCES supplier_bill (id),
    amount_rupees NUMERIC(12, 2) NOT NULL CHECK (amount_rupees > 0),
    PRIMARY KEY (payment_id, bill_id)
);

CREATE INDEX supplier_payment_allocation_bill ON supplier_payment_allocation (bill_id);

-- --- TDS challans --------------------------------------------------------------------------------------------------
CREATE TABLE tds_challan (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tds_section_id BIGINT         NOT NULL REFERENCES tds_section (id),
    period_month   DATE           NOT NULL CHECK (EXTRACT(DAY FROM period_month) = 1),
    amount_rupees  NUMERIC(12, 2) NOT NULL CHECK (amount_rupees > 0),
    interest_rupees NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (interest_rupees >= 0),
    challan_no     VARCHAR(20)    NOT NULL,
    bsr_code       VARCHAR(7)     NOT NULL,
    paid_on        DATE           NOT NULL,
    mode           VARCHAR(8)     NOT NULL CHECK (mode IN ('CASH', 'UPI', 'CARD', 'BANK')),
    created_by     VARCHAR(120),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- --- Fixed assets --------------------------------------------------------------------------------------------------
CREATE TABLE fixed_asset (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                    VARCHAR(120)   NOT NULL,
    asset_class_id          BIGINT         NOT NULL REFERENCES asset_class (id),
    cost_rupees             NUMERIC(12, 2) NOT NULL CHECK (cost_rupees > 0),
    put_to_use_on           DATE           NOT NULL,
    bill_line_id            BIGINT UNIQUE REFERENCES supplier_bill_line (id),
    opening_accdep_rupees   NUMERIC(12, 2) NOT NULL DEFAULT 0
                            CHECK (opening_accdep_rupees >= 0 AND opening_accdep_rupees <= cost_rupees),
    disposed_on             DATE,
    disposal_proceeds_rupees NUMERIC(12, 2) CHECK (disposal_proceeds_rupees >= 0),
    disposal_mode           VARCHAR(8) CHECK (disposal_mode IN ('CASH', 'UPI', 'CARD', 'BANK')),
    created_by              VARCHAR(120),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT fixed_asset_disposed CHECK (disposed_on IS NULL OR disposed_on >= put_to_use_on)
);

-- One row per posted month; the pair is the idempotency guard for depreciation.
CREATE TABLE asset_depreciation (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_id      BIGINT         NOT NULL REFERENCES fixed_asset (id),
    month         DATE           NOT NULL CHECK (EXTRACT(DAY FROM month) = 1),
    amount_rupees NUMERIC(12, 2) NOT NULL CHECK (amount_rupees > 0),
    CONSTRAINT asset_depreciation_month UNIQUE (asset_id, month)
);

-- --- Books settings ------------------------------------------------------------------------------------------------
ALTER TABLE books_setting ADD COLUMN shiprocket_party_id BIGINT REFERENCES party (id);

-- --- Guards --------------------------------------------------------------------------------------------------------
-- A bill whose ISSUE entry is posted can only be cancelled; changing what it says about money needs a cancel and a new
-- bill. status, cancellation fields, the attachment and notes stay editable.
CREATE FUNCTION supplier_bill_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND (OLD.party_id, OLD.bill_no, OLD.bill_date, OLD.due_date, OLD.supply_state_code, OLD.taxable_rupees,
            OLD.tax_rupees, OLD.total_rupees, OLD.tds_section_id, OLD.tds_rupees, OLD.payable_rupees, OLD.source,
            OLD.created_by, OLD.created_at)
           IS NOT DISTINCT FROM
           (NEW.party_id, NEW.bill_no, NEW.bill_date, NEW.due_date, NEW.supply_state_code, NEW.taxable_rupees,
            NEW.tax_rupees, NEW.total_rupees, NEW.tds_section_id, NEW.tds_rupees, NEW.payable_rupees, NEW.source,
            NEW.created_by, NEW.created_at) THEN
        RETURN NEW;
    END IF;
    IF EXISTS (SELECT 1 FROM journal_entry
               WHERE source_type = 'SUPPLIER_BILL' AND source_id = OLD.id AND source_step = 'ISSUE') THEN
        RAISE EXCEPTION 'This bill is in the books; cancel it instead';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER supplier_bill_protect BEFORE UPDATE OR DELETE ON supplier_bill
    FOR EACH ROW EXECUTE FUNCTION supplier_bill_protect();

-- Lines of a posted bill are fixed too.
CREATE FUNCTION supplier_bill_line_protect() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    bill BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        bill := OLD.bill_id;
    ELSE
        bill := NEW.bill_id;
    END IF;
    IF EXISTS (SELECT 1 FROM journal_entry
               WHERE source_type = 'SUPPLIER_BILL' AND source_id = bill AND source_step = 'ISSUE') THEN
        RAISE EXCEPTION 'This bill is in the books; cancel it instead';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER supplier_bill_line_protect BEFORE INSERT OR UPDATE OR DELETE ON supplier_bill_line
    FOR EACH ROW EXECUTE FUNCTION supplier_bill_line_protect();

-- A posted payment is corrected by a reversal and a new payment.
CREATE FUNCTION supplier_payment_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM journal_entry
               WHERE source_type = 'SUPPLIER_PAYMENT' AND source_id = OLD.id AND source_step = 'PAYMENT') THEN
        RAISE EXCEPTION 'This payment is in the books; reverse it instead';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER supplier_payment_protect BEFORE UPDATE OR DELETE ON supplier_payment
    FOR EACH ROW EXECUTE FUNCTION supplier_payment_protect();

CREATE FUNCTION supplier_payment_allocation_protect() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    payment BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        payment := OLD.payment_id;
    ELSE
        payment := NEW.payment_id;
    END IF;
    IF EXISTS (SELECT 1 FROM journal_entry
               WHERE source_type = 'SUPPLIER_PAYMENT' AND source_id = payment AND source_step = 'PAYMENT') THEN
        RAISE EXCEPTION 'This payment is in the books; reverse it instead';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER supplier_payment_allocation_protect BEFORE INSERT OR UPDATE OR DELETE
    ON supplier_payment_allocation
    FOR EACH ROW EXECUTE FUNCTION supplier_payment_allocation_protect();

CREATE FUNCTION tds_challan_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM journal_entry
               WHERE source_type = 'TDS_CHALLAN' AND source_id = OLD.id AND source_step = 'PAYMENT') THEN
        RAISE EXCEPTION 'This challan is in the books; reverse it instead';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tds_challan_protect BEFORE UPDATE OR DELETE ON tds_challan
    FOR EACH ROW EXECUTE FUNCTION tds_challan_protect();

-- Once any month of depreciation is posted the asset cannot go away and its cost or life inputs cannot change; a
-- rename or a disposal is still allowed.
CREATE FUNCTION fixed_asset_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1
               FROM asset_depreciation d
               JOIN journal_entry e ON e.source_type = 'ASSET_DEPRECIATION' AND e.source_id = d.id
               WHERE d.asset_id = OLD.id) THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'This asset has depreciation in the books';
        END IF;
        IF (OLD.cost_rupees, OLD.asset_class_id, OLD.put_to_use_on, OLD.opening_accdep_rupees)
           IS DISTINCT FROM
           (NEW.cost_rupees, NEW.asset_class_id, NEW.put_to_use_on, NEW.opening_accdep_rupees) THEN
            RAISE EXCEPTION 'This asset has depreciation in the books';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER fixed_asset_protect BEFORE UPDATE OR DELETE ON fixed_asset
    FOR EACH ROW EXECUTE FUNCTION fixed_asset_protect();

-- --- Audit log -----------------------------------------------------------------------------------------------------
-- audit_row() keys a row by its id, so supplier_payment_allocation (a composite key, no id) is left out.
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['tds_section', 'asset_class', 'supplier_bill', 'supplier_bill_line',
                             'supplier_payment', 'tds_challan', 'fixed_asset', 'asset_depreciation']
    LOOP
        EXECUTE format('CREATE TRIGGER audit_%1$s AFTER INSERT OR UPDATE OR DELETE ON %1$I
                        FOR EACH ROW EXECUTE FUNCTION audit_row()', t);
    END LOOP;
END;
$$;
