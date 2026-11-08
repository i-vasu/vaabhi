-- What a storefront customer keeps on their account page: date of birth, a delivery address and their
-- measurements (filled in against the shop's garment templates, e.g. Blouse → bust, waist…).
ALTER TABLE customer
    ADD COLUMN date_of_birth DATE,
    ADD COLUMN address_line1 VARCHAR(200),
    ADD COLUMN address_line2 VARCHAR(200),
    ADD COLUMN city          VARCHAR(80),
    ADD COLUMN state         VARCHAR(80),
    ADD COLUMN pincode       VARCHAR(6) CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    -- Garment template id → field key → value as typed, e.g. {"1": {"bust": "36", "waist": "30"}}.
    ADD COLUMN measurements  JSONB NOT NULL DEFAULT '{}'::jsonb;
