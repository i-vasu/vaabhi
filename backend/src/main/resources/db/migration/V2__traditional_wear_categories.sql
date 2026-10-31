-- Replace the scaffold categories with Vaabhi's women's traditional wear range.
-- Safe while no products reference categories (the catalogue module comes later).
DELETE FROM category;

INSERT INTO category (slug, name, position) VALUES
    ('sarees', 'Sarees', 1),
    ('lehengas', 'Lehengas', 2),
    ('kurta-sets', 'Kurta sets', 3),
    ('salwar-suits', 'Salwar suits', 4),
    ('dupattas', 'Dupattas', 5);
