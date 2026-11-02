-- Shop staff who sign in to the admin dashboard. V4 is left to the customer sign-in migration (feat/auth); see
-- docs/ADMIN_PORTAL.md → Database migrations.
-- Passwords are stored only as BCrypt hashes. Repeated failures lock the account for a short while.

CREATE TABLE staff_account (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'STAFF')),
    password_hash VARCHAR(100) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_logins INT          NOT NULL DEFAULT 0,
    locked_until  TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Email is the sign-in name; compare it case-insensitively.
CREATE UNIQUE INDEX staff_account_email_uq ON staff_account (lower(email));
