-- Order flow fixes: points redeemed on the booking itself, how money paid on a cancelled order was settled.

ALTER TABLE shop_order
    ADD COLUMN reward_points     INT          NOT NULL DEFAULT 0 CHECK (reward_points >= 0),
    ADD COLUMN reward_paise      BIGINT       NOT NULL DEFAULT 0 CHECK (reward_paise >= 0),
    ADD COLUMN cancelled_at      TIMESTAMPTZ,
    ADD COLUMN cancel_settlement VARCHAR(8) CHECK (cancel_settlement IN ('NONE', 'REFUNDED', 'KEPT')),
    ADD COLUMN cancel_reason     VARCHAR(200);

-- Points redeemed on an order come back (once) when it is cancelled.
ALTER TABLE reward_entry DROP CONSTRAINT reward_entry_kind_check;
ALTER TABLE reward_entry
    ADD CONSTRAINT reward_entry_kind_check CHECK (kind IN ('EARNED', 'REDEEMED', 'ADJUSTED', 'RETURNED'));
CREATE UNIQUE INDEX reward_entry_returned_once ON reward_entry (order_id) WHERE kind = 'RETURNED';
