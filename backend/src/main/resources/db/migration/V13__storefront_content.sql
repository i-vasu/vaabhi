-- Storefront content managed from the dashboard: home-page banners (hero slides, occasion tiles, the craft
-- section) and a photo per category (its circle on the home page and the banner on its page). Photos live in R2
-- under site/<uuid>/{400,800,1600}.webp; only the key is stored.

CREATE TABLE site_banner (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- HERO: the big slides at the top; OCCASION: the "Shop by occasion" tiles; CRAFT: the craft section photo.
    slot       VARCHAR(10)  NOT NULL CHECK (slot IN ('HERO', 'OCCASION', 'CRAFT')),
    position   INT          NOT NULL DEFAULT 0,
    eyebrow    VARCHAR(60),
    title      VARCHAR(120) NOT NULL,
    subtitle   VARCHAR(240),
    cta_label  VARCHAR(40),
    -- A storefront path such as /c/sarees or /occasion/wedding.
    link       VARCHAR(200),
    image_key  VARCHAR(200) NOT NULL,
    alt_text   VARCHAR(200),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Shown only between these days (inclusive) when set, e.g. a festive sale banner.
    starts_on  DATE,
    ends_on    DATE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on)
);

CREATE INDEX site_banner_slot ON site_banner (slot, position, id);

ALTER TABLE category ADD COLUMN image_key VARCHAR(200);
