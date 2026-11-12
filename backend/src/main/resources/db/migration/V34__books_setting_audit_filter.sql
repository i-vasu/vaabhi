-- books_setting is also written by every sweep (last_sweep_at); only changes to the start date or the payment
-- account map are worth an audit row.
DROP TRIGGER audit_books_setting ON books_setting;

CREATE TRIGGER audit_books_setting AFTER INSERT OR DELETE ON books_setting
    FOR EACH ROW EXECUTE FUNCTION audit_row();

CREATE TRIGGER audit_books_setting_update AFTER UPDATE ON books_setting
    FOR EACH ROW
    WHEN ((OLD.start_date, OLD.mode_accounts) IS DISTINCT FROM (NEW.start_date, NEW.mode_accounts))
    EXECUTE FUNCTION audit_row();
