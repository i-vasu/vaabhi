-- Storefront content pages written in the dashboard: policies, size and care guides, FAQs, about, weavers.
-- The footer lists the published ones by section; the storefront serves POLICY pages at /policies/<slug> and the
-- others at /<slug>.
CREATE TABLE content_page (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug       VARCHAR(60)  NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    -- HELP, POLICY or ABOUT: the footer column it is listed under.
    section    VARCHAR(10)  NOT NULL CHECK (section IN ('HELP', 'POLICY', 'ABOUT')),
    position   INT          NOT NULL DEFAULT 0,
    title      VARCHAR(120) NOT NULL,
    -- Short summary for search engines and link previews.
    summary    VARCHAR(300),
    -- Plain text: blank lines separate paragraphs, "## " starts a heading, "- " a bullet.
    body       TEXT         NOT NULL,
    published  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX content_page_section ON content_page (section, position, id);
