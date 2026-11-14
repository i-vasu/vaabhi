-- Guards found in the whole-branch review of the general ledger: an expense that is in the books cannot change what
-- it says about money (a correction is a new entry), and changes to the books settings are audited.

CREATE FUNCTION expense_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND (OLD.spent_on, OLD.category, OLD.mode, OLD.amount_rupees)
           IS NOT DISTINCT FROM (NEW.spent_on, NEW.category, NEW.mode, NEW.amount_rupees) THEN
        RETURN NEW;
    END IF;
    IF EXISTS (SELECT 1 FROM journal_entry WHERE source_type = 'EXPENSE' AND source_id = OLD.id) THEN
        RAISE EXCEPTION 'This expense is in the books; record a correction';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER expense_protect BEFORE UPDATE OR DELETE ON expense
    FOR EACH ROW EXECUTE FUNCTION expense_protect();

CREATE TRIGGER audit_books_setting AFTER INSERT OR UPDATE OR DELETE ON books_setting
    FOR EACH ROW EXECUTE FUNCTION audit_row();
