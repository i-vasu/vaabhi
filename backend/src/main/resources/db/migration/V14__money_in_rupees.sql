-- Money is stored in rupees with two decimals (NUMERIC(12,2)), not in whole paise: 14250.50 instead of 1425050.
-- Amounts stay exact. Every *_paise column becomes *_rupees (value / 100), and so do the CHECK constraints named
-- after it.

DO $$
DECLARE
    col record;
    con record;
BEGIN
    FOR col IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public' AND column_name LIKE '%\_paise' AND column_name <> 'paise_per_point'
    LOOP
        EXECUTE format('ALTER TABLE %I RENAME COLUMN %I TO %I', col.table_name, col.column_name,
                       replace(col.column_name, '_paise', '_rupees'));
        EXECUTE format('ALTER TABLE %I ALTER COLUMN %I TYPE NUMERIC(12, 2) USING %I / 100.0', col.table_name,
                       replace(col.column_name, '_paise', '_rupees'), replace(col.column_name, '_paise', '_rupees'));
    END LOOP;

    FOR con IN
        SELECT conrelid::regclass::text AS table_name, conname
        FROM pg_constraint
        WHERE contype = 'c' AND conname LIKE '%paise%' AND conname NOT LIKE '%paise_per_point%'
          AND connamespace = 'public'::regnamespace
    LOOP
        EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I', con.table_name, con.conname,
                       replace(con.conname, 'paise', 'rupees'));
    END LOOP;
END
$$;

-- Discounts and coupons: rupees for AMOUNT, percent for PERCENT (10.5 = 10.5 %). Both were stored × 100
-- (paise, and basis points).
ALTER TABLE coupon DROP CONSTRAINT coupon_check;
ALTER TABLE coupon ALTER COLUMN value TYPE NUMERIC(12, 2) USING value / 100.0;
ALTER TABLE coupon ADD CONSTRAINT coupon_percent_check CHECK (kind <> 'PERCENT' OR value <= 100);

ALTER TABLE shop_order ALTER COLUMN discount_value TYPE NUMERIC(12, 2) USING discount_value / 100.0;

-- Rewards: what one point is worth, in rupees (0 – 100).
ALTER TABLE reward_settings DROP CONSTRAINT reward_settings_paise_per_point_check;
ALTER TABLE reward_settings RENAME COLUMN paise_per_point TO rupees_per_point;
ALTER TABLE reward_settings ALTER COLUMN rupees_per_point TYPE NUMERIC(10, 2) USING rupees_per_point / 100.0;
ALTER TABLE reward_settings ALTER COLUMN rupees_per_point SET DEFAULT 1;
ALTER TABLE reward_settings
    ADD CONSTRAINT reward_settings_rupees_per_point_check CHECK (rupees_per_point >= 0 AND rupees_per_point <= 100);

-- Piece rates per production stage: [{"stageId": 1, "paise": 15000}] → [{"stageId": 1, "rupees": 150.00}].
UPDATE service_product
SET stage_costs = COALESCE(
        (SELECT jsonb_agg((c - 'paise') || jsonb_build_object('rupees', round((c ->> 'paise')::numeric / 100, 2)))
         FROM jsonb_array_elements(stage_costs) c),
        '[]'::jsonb)
WHERE stage_costs @> '[{}]' AND jsonb_array_length(stage_costs) > 0;
