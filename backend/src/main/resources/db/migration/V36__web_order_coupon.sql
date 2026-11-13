-- A coupon applied to an online order, checked and locked at checkout. coupon_code is the code as stored on the
-- coupon table (upper case); discount_rupees is what it took off the items, copied here so later changes to the
-- coupon never alter what the customer was charged.
ALTER TABLE web_order
    ADD COLUMN coupon_code VARCHAR(40),
    ADD COLUMN discount_rupees NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (discount_rupees >= 0);
