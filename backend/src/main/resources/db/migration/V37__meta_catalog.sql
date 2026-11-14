-- Meta (Instagram / WhatsApp) catalogues need JPEG photos; the app makes a JPEG copy of each product photo
-- (<key>/feed.jpg) and marks it here once it is in storage.
ALTER TABLE product_media ADD COLUMN feed_image BOOLEAN NOT NULL DEFAULT FALSE;

-- What the Catalog API last sent to Meta, per variant (sku = the Meta item id). The sync compares a hash of each
-- item's fields with this and only sends what changed; rows whose item is gone are deleted from Meta.
CREATE TABLE meta_catalog_item (
    sku        VARCHAR(64) PRIMARY KEY,
    product_id BIGINT      NOT NULL,
    hash       CHAR(64)    NOT NULL,
    synced_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
