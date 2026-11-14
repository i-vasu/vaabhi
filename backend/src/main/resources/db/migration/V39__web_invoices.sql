-- Phase 0 of the books plan: invoices for web orders, and every invoice carries a frozen copy of who bought and who
-- sold, so a later change to a client record or the shop profile never rewrites an issued invoice.

-- An invoice belongs to a shop order or to a web order, never both.
ALTER TABLE invoice
    ALTER COLUMN order_id DROP NOT NULL,
    ALTER COLUMN client_id DROP NOT NULL,
    ADD COLUMN web_order_id BIGINT REFERENCES web_order (id),
    ADD COLUMN party_id BIGINT REFERENCES party (id),
    ADD CONSTRAINT invoice_one_order_chk CHECK (num_nonnulls(order_id, web_order_id) = 1);

-- One live invoice per web order.
CREATE UNIQUE INDEX invoice_live_web_order_idx ON invoice (web_order_id)
    WHERE cancelled_at IS NULL AND web_order_id IS NOT NULL;

-- Snapshot of the document heading, the buyer and the seller as issued.
ALTER TABLE invoice
    ADD COLUMN title VARCHAR(20),
    ADD COLUMN buyer_name VARCHAR(160),
    ADD COLUMN buyer_phone VARCHAR(16),
    ADD COLUMN buyer_address VARCHAR(400),
    ADD COLUMN buyer_state VARCHAR(80),
    ADD COLUMN seller_name VARCHAR(160),
    ADD COLUMN seller_address VARCHAR(300),
    ADD COLUMN seller_cin VARCHAR(21),
    ADD COLUMN seller_state VARCHAR(80),
    ADD COLUMN seller_state_code CHAR(2);

-- Existing invoices: the buyer from the client, the seller from the shop profile as it is now.
UPDATE invoice i SET
    party_id = c.party_id,
    buyer_name = trim(c.first_name || ' ' || coalesce(c.last_name, '')),
    buyer_phone = c.phone,
    buyer_address = nullif(concat_ws(', ', c.address, c.city, c.pin_code), ''),
    buyer_state = c.state
FROM client c
WHERE c.id = i.client_id;

UPDATE invoice i SET
    title = CASE WHEN i.shop_gstin IS NOT NULL THEN 'Tax Invoice' ELSE 'Invoice' END,
    seller_name = coalesce(
        (SELECT nullif(value ->> 'shopName', '') FROM shop_setting WHERE section = 'shop-profile'), 'vaabhi'),
    seller_address = (SELECT nullif(value ->> 'address', '') FROM shop_setting WHERE section = 'shop-profile'),
    seller_state = (SELECT nullif(value ->> 'state', '') FROM shop_setting WHERE section = 'shop-profile');

UPDATE invoice SET buyer_name = 'Customer' WHERE buyer_name IS NULL;

ALTER TABLE invoice
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN buyer_name SET NOT NULL,
    ALTER COLUMN seller_name SET NOT NULL;
