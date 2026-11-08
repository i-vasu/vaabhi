-- Customer accounts and sign-in: Google or Indian mobile number + one-time code (OTP).
-- Codes and refresh tokens are stored only as hashes; a database leak reveals neither.

CREATE TABLE customer (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(80),
    email         VARCHAR(254) UNIQUE,
    -- E.164, e.g. +919876543210; set only once verified by OTP.
    phone         VARCHAR(16)  UNIQUE CHECK (phone ~ '^\+91[6-9][0-9]{9}$'),
    -- Google's stable account id ("sub" claim); emails can change, this cannot.
    google_sub    VARCHAR(255) UNIQUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (phone IS NOT NULL OR google_sub IS NOT NULL)
);

-- One row per code sent. The newest unconsumed row for a phone is the live one.
CREATE TABLE otp_challenge (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phone       VARCHAR(16) NOT NULL,
    code_hash   CHAR(64)    NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);
CREATE INDEX otp_challenge_phone_created ON otp_challenge (phone, created_at DESC);
CREATE INDEX otp_challenge_created ON otp_challenge (created_at);

-- Long-lived sign-in ("stay logged in"). Rotated on every use; reusing a rotated token revokes the family.
CREATE TABLE refresh_token (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT      NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    -- Ended: rotated, logged out, or revoked because a rotated token was reused.
    revoked_at  TIMESTAMPTZ,
    -- Set only when ended by rotation; such a token still works for a few seconds (parallel refreshes).
    rotated_at  TIMESTAMPTZ
);
CREATE INDEX refresh_token_customer ON refresh_token (customer_id);
CREATE INDEX refresh_token_expires ON refresh_token (expires_at);
