-- Meta (Instagram / WhatsApp) catalogues need JPEG photos; the app makes a JPEG copy of each product photo
-- (<key>/feed.jpg) and marks it here once it is in storage.
ALTER TABLE product_media ADD COLUMN feed_image BOOLEAN NOT NULL DEFAULT FALSE;
