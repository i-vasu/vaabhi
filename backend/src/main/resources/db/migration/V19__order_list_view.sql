-- One list of orders for the shop: counter orders (shop_order) and website orders (web_order), so the admin
-- Orders page can show both, page through them and count them together.
--
-- source: SHOP or ONLINE. status uses the shop's words so the page's filters work for both: a website order that
-- is waiting to ship counts as BOOKED and a shipped one as READY; online_status keeps the website order's own
-- status for display. Website orders that were never paid (PENDING_PAYMENT, FAILED) are left out. open_return marks
-- website orders with a return still being handled (requested or approved).
-- Changing a column used here needs the view dropped and created again in the same migration.

CREATE VIEW order_list AS
SELECT 'SHOP'                                         AS source,
       o.id,
       o.number,
       o.client_id,
       c.first_name,
       c.last_name,
       c.phone,
       o.status::varchar                               AS status,
       NULL::varchar                                   AS online_status,
       o.order_date,
       o.delivery_date,
       o.trial_date,
       o.total_rupees,
       o.paid_rupees,
       (SELECT COALESCE(SUM(i.quantity), 0) FROM order_item i WHERE i.order_id = o.id)      AS item_count,
       (SELECT string_agg(i.description, ', ' ORDER BY i.position) FROM order_item i
        WHERE i.order_id = o.id)                                                          AS items_label,
       o.created_at,
       FALSE                                                                             AS open_return
FROM shop_order o
         JOIN client c ON c.id = o.client_id
UNION ALL
SELECT 'ONLINE',
       w.id,
       w.number,
       NULL::bigint,
       w.contact_name,
       NULL::varchar,
       w.contact_phone,
       CASE w.status WHEN 'PLACED' THEN 'BOOKED' WHEN 'SHIPPED' THEN 'READY' ELSE w.status END::varchar,
       w.status::varchar,
       (w.created_at AT TIME ZONE 'Asia/Kolkata')::date,
       NULL::date,
       NULL::date,
       w.total_rupees,
       CASE WHEN w.paid_at IS NOT NULL THEN w.total_rupees ELSE 0 END,
       (SELECT COALESCE(SUM(l.quantity), 0) FROM web_order_line l WHERE l.order_id = w.id),
       (SELECT string_agg(l.name, ', ' ORDER BY l.position) FROM web_order_line l WHERE l.order_id = w.id),
       w.created_at,
       EXISTS (SELECT 1 FROM web_order_return r WHERE r.order_id = w.id AND r.status IN ('REQUESTED', 'APPROVED'))
FROM web_order w
WHERE w.status IN ('PLACED', 'SHIPPED', 'DELIVERED', 'CANCELLED');
