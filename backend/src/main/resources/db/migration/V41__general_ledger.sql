-- Phase 2 of the books plan: a double-entry general ledger. A chart of accounts, journal entries that can never be
-- changed once posted, month locks, an audit log of every money-touching row, and the JOURNAL number series.

-- --- Chart of accounts ---------------------------------------------------------------------------------------------
-- Accounts with a system_key are wired into posting rules and cannot be deleted or deactivated.
CREATE TABLE account (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       CHAR(4)     NOT NULL UNIQUE CHECK (code ~ '^[0-9]{4}$'),
    name       VARCHAR(80) NOT NULL,
    type       VARCHAR(9)  NOT NULL CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE')),
    parent_id  BIGINT REFERENCES account (id),
    system_key VARCHAR(40) UNIQUE,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO account (code, name, type, system_key) VALUES
    ('1000', 'Cash in hand', 'ASSET', 'CASH'),
    ('1010', 'Bank', 'ASSET', 'BANK'),
    ('1020', 'Razorpay clearing', 'ASSET', 'RAZORPAY_CLEARING'),
    ('1030', 'Shiprocket COD clearing', 'ASSET', 'COD_CLEARING'),
    ('1100', 'Debtors', 'ASSET', 'DEBTORS'),
    ('1200', 'Input GST', 'ASSET', 'INPUT_GST'),
    ('1300', 'Stock', 'ASSET', 'STOCK'),
    ('2000', 'Creditors', 'LIABILITY', 'CREDITORS'),
    ('2100', 'Customer advances', 'LIABILITY', 'CUSTOMER_ADVANCES'),
    ('2200', 'Output CGST', 'LIABILITY', 'OUTPUT_CGST'),
    ('2210', 'Output SGST', 'LIABILITY', 'OUTPUT_SGST'),
    ('2220', 'Output IGST', 'LIABILITY', 'OUTPUT_IGST'),
    ('2300', 'Salary payable', 'LIABILITY', 'SALARY_PAYABLE'),
    ('2400', 'TDS payable', 'LIABILITY', 'TDS_PAYABLE'),
    ('2500', 'Owner loan', 'LIABILITY', 'OWNER_LOAN'),
    ('3000', 'Share capital', 'EQUITY', 'SHARE_CAPITAL'),
    ('3100', 'Opening balance equity', 'EQUITY', 'OPENING_BALANCE'),
    ('3200', 'Retained earnings', 'EQUITY', 'RETAINED_EARNINGS'),
    ('4000', 'Sales', 'INCOME', 'SALES'),
    ('4010', 'Sales returns', 'INCOME', 'SALES_RETURNS'),
    ('4100', 'Alteration income', 'INCOME', 'ALTERATION_INCOME'),
    ('5000', 'Rent', 'EXPENSE', 'EXPENSE_RENT'),
    ('5010', 'Salaries', 'EXPENSE', 'EXPENSE_SALARY'),
    ('5020', 'Materials', 'EXPENSE', 'EXPENSE_MATERIAL'),
    ('5030', 'Utilities', 'EXPENSE', 'EXPENSE_UTILITIES'),
    ('5040', 'Transport', 'EXPENSE', 'EXPENSE_TRANSPORT'),
    ('5050', 'Maintenance', 'EXPENSE', 'EXPENSE_MAINTENANCE'),
    ('5060', 'Marketing', 'EXPENSE', 'EXPENSE_MARKETING'),
    ('5090', 'Other', 'EXPENSE', 'EXPENSE_OTHER');

CREATE FUNCTION account_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.system_key IS NOT NULL THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'System accounts cannot be deleted';
        END IF;
        IF NEW.system_key IS DISTINCT FROM OLD.system_key OR NEW.active = FALSE THEN
            RAISE EXCEPTION 'System accounts cannot be deactivated or re-keyed';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER account_protect BEFORE UPDATE OR DELETE ON account
    FOR EACH ROW EXECUTE FUNCTION account_protect();

-- --- Journal -------------------------------------------------------------------------------------------------------
-- order_key ties an entry to an order (SHOP:<shop_order id> or WEB:<web_order id>) so an order's advances are one query.
CREATE TABLE journal_entry (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number      VARCHAR(30)  NOT NULL UNIQUE,
    entry_date  DATE         NOT NULL,
    source_type VARCHAR(20)  NOT NULL CHECK (source_type IN ('INVOICE', 'CREDIT_NOTE', 'ORDER_PAYMENT',
                                                              'ALTERATION_PAYMENT', 'WEB_ORDER', 'WEB_RETURN',
                                                              'EXPENSE', 'MANUAL', 'OPENING')),
    source_id   BIGINT,
    source_step VARCHAR(10)  NOT NULL CHECK (source_step IN ('ISSUE', 'APPLY', 'CANCEL', 'RECEIPT', 'REFUND',
                                                              'EXPENSE', 'MANUAL', 'OPENING', 'REVERSAL')),
    narration   VARCHAR(200) NOT NULL,
    order_key   VARCHAR(24),
    reverses_id BIGINT UNIQUE REFERENCES journal_entry (id),
    created_by  VARCHAR(120),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX journal_entry_source ON journal_entry (source_type, source_id, source_step)
    WHERE source_id IS NOT NULL;
CREATE INDEX journal_entry_date ON journal_entry (entry_date DESC, id DESC);
CREATE INDEX journal_entry_order_key ON journal_entry (order_key) WHERE order_key IS NOT NULL;

CREATE TABLE journal_line (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entry_id      BIGINT         NOT NULL REFERENCES journal_entry (id),
    position      INT            NOT NULL,
    account_id    BIGINT         NOT NULL REFERENCES account (id),
    party_id      BIGINT REFERENCES party (id),
    debit_rupees  NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (debit_rupees >= 0),
    credit_rupees NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (credit_rupees >= 0),
    memo          VARCHAR(120),
    CONSTRAINT journal_line_one_side CHECK ((debit_rupees > 0) <> (credit_rupees > 0))
);

CREATE INDEX journal_line_entry ON journal_line (entry_id, position);
CREATE INDEX journal_line_account ON journal_line (account_id, entry_id);
CREATE INDEX journal_line_party ON journal_line (party_id) WHERE party_id IS NOT NULL;

-- --- Period locks, sweep problems ----------------------------------------------------------------------------------
CREATE TABLE period_lock (
    month     DATE        PRIMARY KEY CHECK (EXTRACT(DAY FROM month) = 1),
    locked_by VARCHAR(120),
    locked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE posting_problem (
    source_type VARCHAR(20)  NOT NULL,
    source_id   BIGINT       NOT NULL,
    source_step VARCHAR(10)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    first_seen  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (source_type, source_id, source_step)
);

-- --- Books settings (single row) -----------------------------------------------------------------------------------
-- mode_accounts maps a payment mode to the account its money lands in.
CREATE TABLE books_setting (
    id            SMALLINT    PRIMARY KEY CHECK (id = 1),
    start_date    DATE,
    mode_accounts JSONB       NOT NULL,
    last_sweep_at TIMESTAMPTZ
);

INSERT INTO books_setting (id, start_date, mode_accounts)
VALUES (1, NULL, jsonb_build_object(
        'CASH', (SELECT id FROM account WHERE system_key = 'CASH'),
        'UPI', (SELECT id FROM account WHERE system_key = 'BANK'),
        'CARD', (SELECT id FROM account WHERE system_key = 'BANK'),
        'BANK', (SELECT id FROM account WHERE system_key = 'BANK'),
        'RAZORPAY', (SELECT id FROM account WHERE system_key = 'RAZORPAY_CLEARING'),
        'COD', (SELECT id FROM account WHERE system_key = 'COD_CLEARING')));

-- --- Journal numbers -----------------------------------------------------------------------------------------------
INSERT INTO number_sequence (kind, prefix, next_value, suffix, pad, reset) VALUES
    ('JOURNAL', 'JV/{FY}/', 1, '', 5, 'FY');

-- --- Journal triggers ----------------------------------------------------------------------------------------------
-- At commit every entry balances and has at least two lines.
CREATE FUNCTION journal_check_balanced() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    entry     BIGINT;
    debits    NUMERIC;
    credits   NUMERIC;
    line_count BIGINT;
    entry_no  VARCHAR;
BEGIN
    IF TG_TABLE_NAME = 'journal_entry' THEN
        entry := NEW.id;
    ELSE
        entry := NEW.entry_id;
    END IF;
    SELECT COALESCE(SUM(debit_rupees), 0), COALESCE(SUM(credit_rupees), 0), COUNT(*)
    INTO debits, credits, line_count
    FROM journal_line WHERE entry_id = entry;
    IF debits <> credits OR line_count < 2 THEN
        SELECT number INTO entry_no FROM journal_entry WHERE id = entry;
        RAISE EXCEPTION 'Journal entry % does not balance', COALESCE(entry_no, entry::text);
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER journal_balanced_line AFTER INSERT OR UPDATE ON journal_line
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION journal_check_balanced();
CREATE CONSTRAINT TRIGGER journal_balanced_entry AFTER INSERT OR UPDATE ON journal_entry
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION journal_check_balanced();

CREATE FUNCTION journal_reject_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Posted journal entries cannot be changed';
END;
$$;

CREATE TRIGGER journal_immutable_entry BEFORE UPDATE OR DELETE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION journal_reject_change();
CREATE TRIGGER journal_immutable_line BEFORE UPDATE OR DELETE ON journal_line
    FOR EACH ROW EXECUTE FUNCTION journal_reject_change();

CREATE FUNCTION journal_check_period_open() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM period_lock WHERE month = date_trunc('month', NEW.entry_date)::date) THEN
        RAISE EXCEPTION '% is locked', to_char(NEW.entry_date, 'FMMonth YYYY');
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER journal_period_open BEFORE INSERT ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION journal_check_period_open();

-- --- Audit log -----------------------------------------------------------------------------------------------------
-- actor comes from vaabhi.actor, which the app sets per transaction when a staff user is known.
CREATE TABLE audit_log (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name VARCHAR(60) NOT NULL,
    row_id     VARCHAR(40),
    action     VARCHAR(6)  NOT NULL CHECK (action IN ('INSERT', 'UPDATE', 'DELETE')),
    old_values JSONB,
    new_values JSONB,
    actor      VARCHAR(120),
    at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_log_row ON audit_log (table_name, row_id, id DESC);
CREATE INDEX audit_log_at ON audit_log (at DESC);

CREATE FUNCTION audit_row() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    old_json JSONB;
    new_json JSONB;
    key_json JSONB;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        old_json := to_jsonb(OLD);
    END IF;
    IF TG_OP <> 'DELETE' THEN
        new_json := to_jsonb(NEW);
    END IF;
    key_json := COALESCE(new_json, old_json);
    INSERT INTO audit_log (table_name, row_id, action, old_values, new_values, actor)
    VALUES (TG_TABLE_NAME, COALESCE(key_json ->> 'id', key_json ->> 'month'), TG_OP, old_json, new_json,
            NULLIF(current_setting('vaabhi.actor', TRUE), ''));
    RETURN NULL;
END;
$$;

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['invoice', 'invoice_line', 'credit_note', 'credit_note_line', 'order_payment',
                             'alteration_payment', 'expense', 'web_order_return', 'account', 'journal_entry',
                             'journal_line', 'period_lock', 'tax_class']
    LOOP
        EXECUTE format('CREATE TRIGGER audit_%1$s AFTER INSERT OR UPDATE OR DELETE ON %1$I
                        FOR EACH ROW EXECUTE FUNCTION audit_row()', t);
    END LOOP;
END;
$$;

-- Web orders change constantly; only changes to what was charged, paid or refunded are worth a row.
CREATE TRIGGER audit_web_order AFTER UPDATE ON web_order
    FOR EACH ROW
    WHEN ((OLD.items_rupees, OLD.shipping_rupees, OLD.total_rupees, OLD.paid_at, OLD.refunded_rupees,
           OLD.refund_status)
          IS DISTINCT FROM
          (NEW.items_rupees, NEW.shipping_rupees, NEW.total_rupees, NEW.paid_at, NEW.refunded_rupees,
           NEW.refund_status))
    EXECUTE FUNCTION audit_row();
