-- DEMO DATA: loaded only with the "dev" Spring profile (local, CI end-to-end tests, the Neon dev branch).
-- Never enable that profile in production. Re-runs whenever this file changes and touches only its own slugs.
--
-- Covers the listing and product page edge cases: one DRAFT product (never visible), one out of stock,
-- one with low stock, sized and one-size products, multi-colour products and blouse add-ons.

CREATE TEMP TABLE demo_product (
    slug       TEXT PRIMARY KEY,
    category   TEXT NOT NULL,
    name       TEXT NOT NULL,
    subtitle   TEXT NOT NULL,
    badge      TEXT,
    popularity INT  NOT NULL,
    days_old   INT  NOT NULL,
    status     TEXT NOT NULL,
    blouse     BOOLEAN NOT NULL,
    price      INT  NOT NULL, -- rupees
    mrp        INT  NOT NULL, -- rupees
    stock      TEXT NOT NULL, -- normal | low | out
    colours    TEXT NOT NULL, -- name:#HEX:family, separated by |
    facets     TEXT[] NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_product VALUES
-- Sarees (one size)
('kanjeevaram-silk-saree-temple-border', 'sarees', 'Kanjeevaram silk saree', 'Temple border, pure zari', 'BESTSELLER', 980, 40, 'PUBLISHED', TRUE, 18500, 22000, 'normal',
 'Rani pink:#C2185B:pink|Mustard:#D4A017:yellow', ARRAY['fabric:silk', 'weave:kanjeevaram', 'work:zari', 'occasion:wedding']),
('banarasi-katan-silk-saree', 'sarees', 'Banarasi katan silk saree', 'Kadhwa booti, antique zari', 'BRIDAL', 940, 25, 'PUBLISHED', TRUE, 24900, 29900, 'normal',
 'Wine:#4E1A24:maroon|Emerald:#1F6E4A:green', ARRAY['fabric:silk', 'weave:banarasi', 'work:zari', 'occasion:wedding']),
('chanderi-cotton-silk-saree', 'sarees', 'Chanderi cotton silk saree', 'Light, with gold butis', 'HANDLOOM', 720, 60, 'PUBLISHED', TRUE, 4200, 5200, 'normal',
 'Ivory:#F2ECE3:white|Peach:#F2B49B:orange', ARRAY['fabric:cotton', 'weave:chanderi', 'work:zari', 'occasion:office']),
('paithani-silk-saree-peacock-pallu', 'sarees', 'Paithani silk saree', 'Peacock pallu', 'BRIDAL', 860, 12, 'PUBLISHED', TRUE, 21500, 21500, 'low',
 'Purple:#5B2A86:purple', ARRAY['fabric:silk', 'weave:paithani', 'work:zari', 'occasion:wedding']),
('pochampally-ikat-silk-saree', 'sarees', 'Pochampally ikat silk saree', 'Double ikat weave', 'HANDLOOM', 610, 90, 'PUBLISHED', TRUE, 9800, 11500, 'normal',
 'Indigo:#2F3E7A:blue|Red:#B3261E:red', ARRAY['fabric:silk', 'weave:ikat', 'occasion:festive']),
('jamdani-cotton-saree', 'sarees', 'Jamdani cotton saree', 'Handwoven in Bengal', 'HANDLOOM', 540, 120, 'PUBLISHED', FALSE, 3600, 4500, 'normal',
 'White:#F7F4EE:white|Black:#2B2622:black', ARRAY['fabric:cotton', 'weave:jamdani', 'occasion:casual']),
('organza-saree-hand-painted', 'sarees', 'Organza saree', 'Hand-painted florals', 'NEW', 690, 3, 'PUBLISHED', TRUE, 6900, 8200, 'normal',
 'Blush:#E8B4B8:pink|Mint:#BFE3D0:green', ARRAY['fabric:organza', 'work:hand-painted', 'occasion:party']),
('georgette-sequin-saree', 'sarees', 'Georgette sequin saree', 'All-over sequins', 'NEW', 650, 5, 'PUBLISHED', TRUE, 5400, 7500, 'normal',
 'Black:#2B2622:black|Champagne:#D9C49A:gold', ARRAY['fabric:georgette', 'work:sequin', 'occasion:party']),
('linen-saree-block-print', 'sarees', 'Linen saree', 'Bagru block print', NULL, 420, 70, 'PUBLISHED', TRUE, 2800, 3400, 'out',
 'Olive:#5E6B3A:green', ARRAY['fabric:linen', 'work:block-print', 'occasion:office']),
('chiffon-saree-printed', 'sarees', 'Chiffon saree', 'Floral print, satin border', NULL, 380, 150, 'PUBLISHED', TRUE, 1900, 2600, 'normal',
 'Coral:#E9735B:orange|Navy:#1F2A44:blue', ARRAY['fabric:chiffon', 'work:printed', 'occasion:casual']),
('crepe-saree-office', 'sarees', 'Crepe saree', 'Easy drape, solid colour', NULL, 300, 200, 'PUBLISHED', TRUE, 1600, 2000, 'normal',
 'Grey:#8C8680:grey|Maroon:#6E2A35:maroon|Teal:#1F6F6B:green', ARRAY['fabric:crepe', 'occasion:office']),
('tissue-silk-saree-draft', 'sarees', 'Tissue silk saree', 'Coming soon', NULL, 999, 1, 'DRAFT', TRUE, 12000, 14000, 'normal',
 'Gold:#A88B5A:gold', ARRAY['fabric:silk', 'occasion:festive']),
-- Lehengas (sized)
('velvet-bridal-lehenga-zardozi', 'lehengas', 'Velvet bridal lehenga', 'Zardozi, with net dupatta', 'BRIDAL', 900, 30, 'PUBLISHED', TRUE, 58000, 65000, 'normal',
 'Red:#B3261E:red|Wine:#4E1A24:maroon', ARRAY['fabric:velvet', 'work:embroidery', 'work:zari', 'occasion:wedding']),
('organza-lehenga-mirror-work', 'lehengas', 'Organza lehenga', 'Mirror work, pastel', 'NEW', 760, 8, 'PUBLISHED', TRUE, 18900, 22900, 'normal',
 'Lilac:#C3A6D8:purple|Powder blue:#A9C6E8:blue', ARRAY['fabric:organza', 'work:mirror', 'occasion:festive']),
('georgette-lehenga-sequin', 'lehengas', 'Georgette lehenga', 'Sequin, for the sangeet', NULL, 640, 45, 'PUBLISHED', TRUE, 14500, 18000, 'normal',
 'Black:#2B2622:black|Rose gold:#C99A86:pink', ARRAY['fabric:georgette', 'work:sequin', 'occasion:party']),
('banarasi-silk-lehenga', 'lehengas', 'Banarasi silk lehenga', 'Brocade skirt, zari border', 'BESTSELLER', 820, 55, 'PUBLISHED', TRUE, 27500, 32000, 'normal',
 'Mustard:#D4A017:yellow|Rani pink:#C2185B:pink', ARRAY['fabric:silk', 'weave:banarasi', 'work:zari', 'occasion:wedding']),
('cotton-lehenga-block-print', 'lehengas', 'Cotton lehenga', 'Block print, for mehendi', NULL, 450, 80, 'PUBLISHED', TRUE, 6800, 8500, 'normal',
 'Green:#3F7D4E:green', ARRAY['fabric:cotton', 'work:block-print', 'occasion:festive']),
('chanderi-lehenga-gota', 'lehengas', 'Chanderi lehenga', 'Gota patti work', 'HANDLOOM', 500, 100, 'PUBLISHED', TRUE, 11500, 13500, 'normal',
 'Peach:#F2B49B:orange|Ivory:#F2ECE3:white', ARRAY['fabric:silk', 'weave:chanderi', 'work:embroidery', 'occasion:festive']),
-- Kurta sets (sized)
('chikankari-kurta-set', 'kurta-sets', 'Chikankari kurta set', 'Kurta, palazzo and dupatta', 'BESTSELLER', 880, 35, 'PUBLISHED', FALSE, 4800, 6200, 'normal',
 'White:#F7F4EE:white|Powder blue:#A9C6E8:blue|Mint:#BFE3D0:green', ARRAY['fabric:cotton', 'work:embroidery', 'occasion:casual']),
('anarkali-kurta-set-georgette', 'kurta-sets', 'Anarkali kurta set', 'Flared, with zari dupatta', 'NEW', 700, 6, 'PUBLISHED', FALSE, 6400, 7900, 'normal',
 'Maroon:#6E2A35:maroon|Bottle green:#1F4D3A:green', ARRAY['fabric:georgette', 'work:zari', 'occasion:festive']),
('cotton-kurta-set-block-print', 'kurta-sets', 'Cotton kurta set', 'Jaipur block print', NULL, 560, 75, 'PUBLISHED', FALSE, 2400, 3200, 'normal',
 'Indigo:#2F3E7A:blue|Mustard:#D4A017:yellow', ARRAY['fabric:cotton', 'work:block-print', 'occasion:office']),
('linen-kurta-set-office', 'kurta-sets', 'Linen kurta set', 'Straight kurta and pants', NULL, 410, 110, 'PUBLISHED', FALSE, 3600, 3600, 'normal',
 'Beige:#E4D3B5:beige|Olive:#5E6B3A:green', ARRAY['fabric:linen', 'occasion:office']),
('silk-kurta-set-festive', 'kurta-sets', 'Silk kurta set', 'Brocade yoke, organza dupatta', NULL, 590, 20, 'PUBLISHED', FALSE, 7900, 9900, 'low',
 'Rani pink:#C2185B:pink', ARRAY['fabric:silk', 'work:zari', 'occasion:festive']),
('sharara-set-mirror', 'kurta-sets', 'Sharara set', 'Mirror work, short kurta', 'NEW', 630, 2, 'PUBLISHED', FALSE, 5600, 6900, 'normal',
 'Yellow:#E3B23C:yellow|Orange:#D9772B:orange', ARRAY['fabric:georgette', 'work:mirror', 'occasion:festive']),
-- Salwar suits (sized)
('patiala-suit-cotton', 'salwar-suits', 'Patiala salwar suit', 'Phulkari dupatta', NULL, 480, 65, 'PUBLISHED', FALSE, 3200, 4200, 'normal',
 'Red:#B3261E:red|Green:#3F7D4E:green', ARRAY['fabric:cotton', 'work:embroidery', 'occasion:festive']),
('chanderi-salwar-suit', 'salwar-suits', 'Chanderi salwar suit', 'Straight cut, zari butis', 'HANDLOOM', 430, 95, 'PUBLISHED', FALSE, 5200, 6000, 'normal',
 'Peach:#F2B49B:orange|Grey:#8C8680:grey', ARRAY['fabric:silk', 'weave:chanderi', 'work:zari', 'occasion:office']),
('georgette-salwar-suit-party', 'salwar-suits', 'Georgette salwar suit', 'Sequin yoke', NULL, 350, 140, 'PUBLISHED', FALSE, 4600, 5800, 'normal',
 'Black:#2B2622:black|Navy:#1F2A44:blue', ARRAY['fabric:georgette', 'work:sequin', 'occasion:party']),
-- Dupattas (one size)
('banarasi-silk-dupatta', 'dupattas', 'Banarasi silk dupatta', 'Zari jaal', 'BESTSELLER', 670, 50, 'PUBLISHED', FALSE, 3900, 4800, 'normal',
 'Red:#B3261E:red|Gold:#A88B5A:gold', ARRAY['fabric:silk', 'weave:banarasi', 'work:zari', 'occasion:wedding']),
('phulkari-dupatta', 'dupattas', 'Phulkari dupatta', 'Hand embroidered', 'HANDLOOM', 520, 85, 'PUBLISHED', FALSE, 2900, 3500, 'normal',
 'Multicolour:#C2185B:multi', ARRAY['fabric:cotton', 'work:embroidery', 'occasion:festive']),
('chiffon-dupatta-printed', 'dupattas', 'Chiffon dupatta', 'Leheriya print', NULL, 330, 160, 'PUBLISHED', FALSE, 900, 1200, 'normal',
 'Pink:#D8698F:pink|Yellow:#E3B23C:yellow|Green:#3F7D4E:green', ARRAY['fabric:chiffon', 'work:printed', 'occasion:casual']);

-- Start clean (cascades to variants, facets, media, add-ons, collection links).
DELETE FROM product WHERE slug IN (SELECT slug FROM demo_product);

INSERT INTO product (slug, name, subtitle, description, category_id, status, badge, care_instructions,
                     saree_length_m, blouse_included, blouse_length_m, popularity, published_at, created_at, updated_at)
SELECT d.slug, d.name, d.subtitle,
       d.name || ' — ' || lower(d.subtitle) || '. Demo product for local development.',
       c.id, d.status, d.badge,
       CASE WHEN d.category = 'sarees' OR d.category = 'lehengas' THEN 'Dry clean only.' ELSE 'Gentle hand wash in cold water.' END,
       CASE WHEN d.category = 'sarees' THEN 5.5 END,
       d.blouse,
       CASE WHEN d.category = 'sarees' AND d.blouse THEN 0.8 END,
       d.popularity,
       CASE WHEN d.status = 'PUBLISHED' THEN now() - make_interval(days => d.days_old) END,
       now() - make_interval(days => d.days_old),
       now() - make_interval(days => d.days_old)
FROM demo_product d
JOIN category c ON c.slug = d.category;

-- Variants: every colour, × sizes XS–XXL for stitched categories. Stock is deterministic per SKU.
INSERT INTO product_variant (product_id, sku, colour_name, colour_hex, colour_family, size, price_paise, mrp_paise,
                             stock_qty, position)
SELECT p.id,
       upper('DEMO-' || left(md5(d.slug), 6) || '-' || colour.ord || coalesce('-' || sz.size, '')),
       split_part(colour.spec, ':', 1),
       split_part(colour.spec, ':', 2),
       split_part(colour.spec, ':', 3),
       sz.size,
       d.price * 100,
       d.mrp * 100,
       CASE d.stock
           WHEN 'out' THEN 0
           WHEN 'low' THEN CASE WHEN colour.ord = 1 AND coalesce(sz.ord, 3) = 3 THEN 2 ELSE 0 END
           ELSE 1 + abs(hashtext(d.slug || colour.ord || coalesce(sz.size, ''))) % 9
       END,
       colour.ord * 10 + coalesce(sz.ord, 0)
FROM demo_product d
JOIN product p ON p.slug = d.slug
CROSS JOIN LATERAL unnest(string_to_array(d.colours, '|')) WITH ORDINALITY AS colour (spec, ord)
LEFT JOIN LATERAL (
    SELECT s.size, s.ord
    FROM unnest(ARRAY['XS', 'S', 'M', 'L', 'XL', 'XXL']) WITH ORDINALITY AS s (size, ord)
    WHERE d.category IN ('lehengas', 'kurta-sets', 'salwar-suits')
) sz ON TRUE;

INSERT INTO product_facet_value (product_id, facet_value_id)
SELECT p.id, fv.id
FROM demo_product d
JOIN product p ON p.slug = d.slug
CROSS JOIN LATERAL unnest(d.facets) AS tag
JOIN facet f ON f.code = split_part(tag, ':', 1)
JOIN facet_value fv ON fv.facet_id = f.id AND fv.code = split_part(tag, ':', 2);

-- Blouse options for sarees that come with a blouse piece, and custom stitching for lehengas.
INSERT INTO product_addon (product_id, kind, code, name, description, price_paise, input, position)
SELECT p.id, 'BLOUSE', a.code, a.name, a.description, a.price_paise, a.input, a.position
FROM demo_product d
JOIN product p ON p.slug = d.slug
JOIN (VALUES
    ('sarees', 'blouse-unstitched', 'Unstitched blouse piece', 'Included with the saree.', 0, 'NONE', 1),
    ('sarees', 'blouse-standard', 'Stitched blouse, standard size', 'Sizes 32 to 44. Ships in 5–7 extra days.', 120000, 'SIZE', 2),
    ('sarees', 'blouse-custom', 'Stitched blouse, made to measure', 'We call you for measurements. Ships in 7–10 extra days.', 180000, 'MEASUREMENTS', 3),
    ('lehengas', 'blouse-custom', 'Made-to-measure blouse', 'Blouse stitched to your measurements. Ships in 7–10 extra days.', 150000, 'MEASUREMENTS', 1)
) AS a (category, code, name, description, price_paise, input, position) ON a.category = d.category
WHERE d.blouse;
