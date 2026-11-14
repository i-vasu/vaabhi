-- Phase 6 of the books plan: every night the backend dumps the books database and the new bill attachments to an
-- Object-Locked S3 bucket in India. Each run is a row in backup_run; each copied attachment a row in backup_file.

CREATE TABLE backup_run (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind                VARCHAR(10) NOT NULL CHECK (kind IN ('DB', 'CHECK', 'DRILL')),
    status              VARCHAR(10) NOT NULL CHECK (status IN ('RUNNING', 'OK', 'FAILED')),
    started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at         TIMESTAMPTZ,
    size_bytes          BIGINT,
    sha256              VARCHAR(64),
    object_key          VARCHAR(200),
    monthly_key         VARCHAR(200),
    attachments_copied  INT NOT NULL DEFAULT 0,
    message             VARCHAR(500),
    notes               VARCHAR(2000),
    drilled_on          DATE,
    created_by          VARCHAR(120)
);

-- One run of each kind at a time.
CREATE UNIQUE INDEX backup_run_one_running ON backup_run (kind) WHERE status = 'RUNNING';
CREATE INDEX backup_run_kind_started ON backup_run (kind, started_at DESC);

-- One row per bill attachment copied to the bucket. The id is only there for the audit log, which keys a row by it.
CREATE TABLE backup_file (
    id          BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    source_key  VARCHAR(200) PRIMARY KEY,
    backup_key  VARCHAR(220) NOT NULL,
    size_bytes  BIGINT NOT NULL,
    sha256      VARCHAR(64) NOT NULL,
    copied_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- --- Audit log -----------------------------------------------------------------------------------------------------
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['backup_run', 'backup_file']
    LOOP
        EXECUTE format('CREATE TRIGGER audit_%1$s AFTER INSERT OR UPDATE OR DELETE ON %1$I
                        FOR EACH ROW EXECUTE FUNCTION audit_row()', t);
    END LOOP;
END;
$$;
