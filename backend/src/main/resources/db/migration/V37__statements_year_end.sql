-- Phase 5 of the books plan: a Schedule III heading on every account, financial years with their closing stock list,
-- and the journal sources the year-end close posts under.

-- --- Schedule III lines --------------------------------------------------------------------------------------------
ALTER TABLE account ADD COLUMN schedule_line VARCHAR(30);

UPDATE account SET schedule_line = CASE type
    WHEN 'ASSET' THEN 'OTHER_CURRENT_ASSETS'
    WHEN 'LIABILITY' THEN 'OTHER_CURRENT_LIABILITIES'
    WHEN 'EQUITY' THEN 'RESERVES_SURPLUS'
    WHEN 'INCOME' THEN 'OTHER_INCOME'
    WHEN 'EXPENSE' THEN 'OTHER_EXPENSES'
END;

UPDATE account SET schedule_line = v.line
FROM (VALUES
    ('SALES', 'REVENUE_FROM_OPERATIONS'),
    ('SALES_RETURNS', 'REVENUE_FROM_OPERATIONS'),
    ('ALTERATION_INCOME', 'REVENUE_FROM_OPERATIONS'),
    ('ASSET_GAIN', 'OTHER_INCOME'),
    ('EXPENSE_MATERIAL', 'COST_OF_MATERIALS'),
    ('PURCHASES', 'PURCHASES_STOCK_IN_TRADE'),
    ('EXPENSE_SALARY', 'EMPLOYEE_BENEFITS'),
    ('DEPRECIATION', 'DEPRECIATION'),
    ('ASSET_LOSS', 'OTHER_EXPENSES'),
    ('SHARE_CAPITAL', 'SHARE_CAPITAL'),
    ('OPENING_BALANCE', 'RESERVES_SURPLUS'),
    ('RETAINED_EARNINGS', 'RESERVES_SURPLUS'),
    ('OWNER_LOAN', 'SHORT_TERM_BORROWINGS'),
    ('CREDITORS', 'TRADE_PAYABLES'),
    ('CUSTOMER_ADVANCES', 'OTHER_CURRENT_LIABILITIES'),
    ('OUTPUT_CGST', 'OTHER_CURRENT_LIABILITIES'),
    ('OUTPUT_SGST', 'OTHER_CURRENT_LIABILITIES'),
    ('OUTPUT_IGST', 'OTHER_CURRENT_LIABILITIES'),
    ('SALARY_PAYABLE', 'OTHER_CURRENT_LIABILITIES'),
    ('TDS_PAYABLE', 'OTHER_CURRENT_LIABILITIES'),
    ('STOCK', 'INVENTORIES'),
    ('DEBTORS', 'TRADE_RECEIVABLES'),
    ('CASH', 'CASH_AND_BANK'),
    ('BANK', 'CASH_AND_BANK'),
    ('RAZORPAY_CLEARING', 'OTHER_CURRENT_ASSETS'),
    ('COD_CLEARING', 'OTHER_CURRENT_ASSETS'),
    ('INPUT_GST', 'OTHER_CURRENT_ASSETS'))
    AS v (system_key, line)
WHERE account.system_key = v.system_key;

-- The two accounts a new asset class creates both belong under PPE.
UPDATE account SET schedule_line = 'PPE'
WHERE id IN (SELECT asset_account_id FROM asset_class
             UNION
             SELECT accdep_account_id FROM asset_class);

ALTER TABLE account ALTER COLUMN schedule_line SET NOT NULL;
ALTER TABLE account ADD CONSTRAINT account_schedule_line_check
    CHECK (schedule_line IN ('REVENUE_FROM_OPERATIONS', 'OTHER_INCOME', 'COST_OF_MATERIALS',
                             'PURCHASES_STOCK_IN_TRADE', 'CHANGE_IN_INVENTORIES', 'EMPLOYEE_BENEFITS',
                             'FINANCE_COSTS', 'DEPRECIATION', 'OTHER_EXPENSES', 'SHARE_CAPITAL',
                             'RESERVES_SURPLUS', 'LONG_TERM_BORROWINGS', 'SHORT_TERM_BORROWINGS',
                             'TRADE_PAYABLES', 'OTHER_CURRENT_LIABILITIES', 'PPE', 'INVENTORIES',
                             'TRADE_RECEIVABLES', 'CASH_AND_BANK', 'OTHER_CURRENT_ASSETS'));

INSERT INTO account (code, name, type, system_key, schedule_line)
VALUES ('5150', 'Change in inventories', 'EXPENSE', 'CHANGE_IN_INVENTORIES', 'CHANGE_IN_INVENTORIES');

-- --- Journal sources -----------------------------------------------------------------------------------------------
ALTER TABLE journal_entry DROP CONSTRAINT journal_entry_source_type_check;
ALTER TABLE journal_entry ADD CONSTRAINT journal_entry_source_type_check
    CHECK (source_type IN ('INVOICE', 'CREDIT_NOTE', 'ORDER_PAYMENT', 'ALTERATION_PAYMENT', 'WEB_ORDER',
                           'WEB_RETURN', 'EXPENSE', 'MANUAL', 'OPENING', 'RAZORPAY_SETTLEMENT', 'COD_REMITTANCE',
                           'BANK_LINE', 'SUPPLIER_BILL', 'SUPPLIER_PAYMENT', 'TDS_CHALLAN', 'ASSET',
                           'ASSET_DEPRECIATION', 'YEAR_END'));

ALTER TABLE journal_entry DROP CONSTRAINT journal_entry_source_step_check;
ALTER TABLE journal_entry ADD CONSTRAINT journal_entry_source_step_check
    CHECK (source_step IN ('ISSUE', 'APPLY', 'CANCEL', 'RECEIPT', 'REFUND', 'EXPENSE', 'MANUAL', 'OPENING',
                           'REVERSAL', 'SETTLE', 'MATCH', 'POST', 'PAYMENT', 'DEPRECIATION', 'DISPOSAL',
                           'STOCK', 'CLOSE'));

-- --- Financial years -----------------------------------------------------------------------------------------------
-- One row per financial year that has had its stock list saved. fy_start is 1 April. The id is only there for the
-- audit log, which keys a row by its id.
CREATE TABLE fiscal_year (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    fy_start               DATE PRIMARY KEY
                           CHECK (EXTRACT(MONTH FROM fy_start) = 4 AND EXTRACT(DAY FROM fy_start) = 1),
    finished_goods_rupees  NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (finished_goods_rupees >= 0),
    stock_note             VARCHAR(300),
    stock_saved_by         VARCHAR(120),
    stock_saved_at         TIMESTAMPTZ,
    closed                 BOOLEAN NOT NULL DEFAULT FALSE
);

-- Material lines of the closing stock list.
CREATE TABLE closing_stock_line (
    id             BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    fy_start       DATE NOT NULL REFERENCES fiscal_year (fy_start),
    stock_item_id  BIGINT NOT NULL REFERENCES stock_item (id),
    quantity       NUMERIC(12, 2) NOT NULL CHECK (quantity >= 0),
    rate_rupees    NUMERIC(12, 2) NOT NULL CHECK (rate_rupees >= 0),
    value_rupees   NUMERIC(12, 2) NOT NULL CHECK (value_rupees >= 0),
    PRIMARY KEY (fy_start, stock_item_id)
);

-- Every close of a year. Its id is the journal source_id, so closing again after a reopen gets fresh entries.
CREATE TABLE year_close_run (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fy_start          DATE NOT NULL REFERENCES fiscal_year (fy_start),
    stock_entry_id    BIGINT REFERENCES journal_entry (id),
    closing_entry_id  BIGINT REFERENCES journal_entry (id),
    closed_by         VARCHAR(120),
    closed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    reopened_by       VARCHAR(120),
    reopened_at       TIMESTAMPTZ
);
CREATE UNIQUE INDEX year_close_run_open ON year_close_run (fy_start) WHERE reopened_at IS NULL;

-- --- Guards -------------------------------------------------------------------------------------------------------
-- A closed year's stock list and finished-goods figure are fixed until it is reopened.
CREATE FUNCTION closing_stock_protect() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    fy    DATE;
    label TEXT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        fy := OLD.fy_start;
    ELSIF TG_OP = 'INSERT' THEN
        fy := NEW.fy_start;
    ELSE
        fy := COALESCE(NEW.fy_start, OLD.fy_start);
    END IF;
    IF EXISTS (SELECT 1 FROM fiscal_year WHERE fy_start = fy AND closed) THEN
        label := to_char(fy, 'YYYY') || '-' || to_char(fy + interval '1 year', 'YY');
        RAISE EXCEPTION '% is closed', label;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER closing_stock_protect BEFORE INSERT OR UPDATE OR DELETE ON closing_stock_line
    FOR EACH ROW EXECUTE FUNCTION closing_stock_protect();

CREATE FUNCTION fiscal_year_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.closed THEN
        RAISE EXCEPTION '% is closed',
            to_char(OLD.fy_start, 'YYYY') || '-' || to_char(OLD.fy_start + interval '1 year', 'YY');
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER fiscal_year_protect BEFORE UPDATE OF finished_goods_rupees, stock_note ON fiscal_year
    FOR EACH ROW EXECUTE FUNCTION fiscal_year_protect();

-- --- Audit log -----------------------------------------------------------------------------------------------------
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['fiscal_year', 'closing_stock_line', 'year_close_run']
    LOOP
        EXECUTE format('CREATE TRIGGER audit_%1$s AFTER INSERT OR UPDATE OR DELETE ON %1$I
                        FOR EACH ROW EXECUTE FUNCTION audit_row()', t);
    END LOOP;
END;
$$;
