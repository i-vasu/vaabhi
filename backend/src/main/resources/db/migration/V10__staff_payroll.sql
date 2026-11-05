-- Staff: people who work at the shop (with or without a dashboard sign-in), what each role may do, attendance,
-- requests (leave, permission, off day, comp-off, salary advance) and monthly payslips. Money is in paise.

-- Tailors often never sign in: email and password become optional, but a password needs an email to sign in with.
ALTER TABLE staff_account
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN password_hash DROP NOT NULL,
    ADD COLUMN designation        VARCHAR(60),
    ADD COLUMN joined_on          DATE,
    -- MONTHLY: salary_paise a month; DAILY: salary_paise a day worked; PIECE: paid by the stages they finish.
    ADD COLUMN pay_type           VARCHAR(8)  NOT NULL DEFAULT 'MONTHLY' CHECK (pay_type IN ('MONTHLY', 'DAILY', 'PIECE')),
    ADD COLUMN salary_paise       BIGINT      NOT NULL DEFAULT 0 CHECK (salary_paise >= 0),
    -- Paid per hour of overtime; also what an hour of lateness costs.
    ADD COLUMN hourly_paise       BIGINT      NOT NULL DEFAULT 0 CHECK (hourly_paise >= 0),
    ADD COLUMN shift_start        TIME        NOT NULL DEFAULT '10:00',
    ADD COLUMN shift_end          TIME        NOT NULL DEFAULT '19:00',
    ADD COLUMN late_grace_minutes INT         NOT NULL DEFAULT 10 CHECK (late_grace_minutes BETWEEN 0 AND 180),
    -- ISO day of the week (1 = Monday … 7 = Sunday), or none.
    ADD COLUMN weekly_off         SMALLINT CHECK (weekly_off BETWEEN 1 AND 7),
    ADD CONSTRAINT staff_account_sign_in_needs_email CHECK (password_hash IS NULL OR email IS NOT NULL);

-- What managers and staff may do. The owner may do everything and has no rows here.
CREATE TABLE role_permission (
    role       VARCHAR(20) NOT NULL CHECK (role IN ('MANAGER', 'STAFF')),
    permission VARCHAR(20) NOT NULL
        CHECK (permission IN ('ORDERS', 'WORKSHOP', 'MONEY', 'STAFF', 'PAYROLL', 'CATALOGUE', 'SETTINGS')),
    PRIMARY KEY (role, permission)
);

INSERT INTO role_permission (role, permission) VALUES
    ('MANAGER', 'ORDERS'), ('MANAGER', 'WORKSHOP'), ('MANAGER', 'MONEY'), ('MANAGER', 'STAFF'),
    ('MANAGER', 'CATALOGUE'),
    ('STAFF', 'ORDERS'), ('STAFF', 'WORKSHOP');

-- One row per person per day they were marked. Late and overtime minutes are worked out from the shift when saved.
CREATE TABLE attendance (
    staff_id         BIGINT       NOT NULL REFERENCES staff_account (id) ON DELETE CASCADE,
    day              DATE         NOT NULL,
    status           VARCHAR(10)  NOT NULL
        CHECK (status IN ('PRESENT', 'HALF_DAY', 'ABSENT', 'LEAVE', 'OFF', 'HOLIDAY', 'COMP_OFF')),
    check_in         TIME,
    check_out        TIME,
    late_minutes     INT          NOT NULL DEFAULT 0 CHECK (late_minutes >= 0),
    overtime_minutes INT          NOT NULL DEFAULT 0 CHECK (overtime_minutes >= 0),
    note             VARCHAR(200),
    marked_by        VARCHAR(120),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (staff_id, day)
);

CREATE INDEX attendance_day ON attendance (day);

CREATE TABLE staff_request (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    staff_id        BIGINT       NOT NULL REFERENCES staff_account (id) ON DELETE CASCADE,
    kind            VARCHAR(10)  NOT NULL CHECK (kind IN ('LEAVE', 'PERMISSION', 'OFF_DAY', 'COMP_OFF', 'ADVANCE')),
    from_date       DATE         NOT NULL,
    to_date         DATE         NOT NULL,
    -- PERMISSION: hours away during the day.
    hours           NUMERIC(4, 1),
    -- ADVANCE: amount asked for.
    amount_paise    BIGINT CHECK (amount_paise > 0),
    reason          VARCHAR(500),
    status          VARCHAR(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_by    VARCHAR(120),
    decided_by      VARCHAR(120),
    decided_at      TIMESTAMPTZ,
    decision_note   VARCHAR(200),
    -- ADVANCE: the payroll month (first day) it was taken back in.
    recovered_month DATE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (to_date >= from_date),
    CHECK (kind <> 'ADVANCE' OR amount_paise IS NOT NULL)
);

CREATE INDEX staff_request_open ON staff_request (status, created_at);
CREATE INDEX staff_request_staff ON staff_request (staff_id, from_date DESC);

-- A month's pay for one person, frozen when the month is finalised.
CREATE TABLE payslip (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    staff_id          BIGINT        NOT NULL REFERENCES staff_account (id),
    month             DATE          NOT NULL CHECK (EXTRACT(DAY FROM month) = 1),
    pay_type          VARCHAR(8)    NOT NULL,
    rate_paise        BIGINT        NOT NULL,
    days_paid         NUMERIC(5, 1) NOT NULL,
    days_unpaid       NUMERIC(5, 1) NOT NULL,
    base_paise        BIGINT        NOT NULL,
    piece_paise       BIGINT        NOT NULL,
    pieces            INT           NOT NULL,
    overtime_minutes  INT           NOT NULL,
    overtime_paise    BIGINT        NOT NULL,
    late_minutes      INT           NOT NULL,
    late_paise        BIGINT        NOT NULL,
    advance_paise     BIGINT        NOT NULL,
    net_paise         BIGINT        NOT NULL,
    finalized_by      VARCHAR(120),
    finalized_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    paid_at           TIMESTAMPTZ,
    paid_mode         VARCHAR(8) CHECK (paid_mode IN ('CASH', 'UPI', 'CARD', 'BANK')),
    -- The SALARY expense written when it was paid.
    expense_id        BIGINT REFERENCES expense (id),
    UNIQUE (staff_id, month)
);
