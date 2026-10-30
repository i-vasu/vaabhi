CREATE TABLE category (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug     VARCHAR(120) NOT NULL UNIQUE,
    name     VARCHAR(120) NOT NULL,
    position INT          NOT NULL DEFAULT 0
);

INSERT INTO category (slug, name, position) VALUES
    ('dresses', 'Dresses', 1),
    ('tops', 'Tops', 2),
    ('ethnic-wear', 'Ethnic Wear', 3),
    ('accessories', 'Accessories', 4);
