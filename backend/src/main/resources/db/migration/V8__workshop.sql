-- The workshop: who is working on each order item, which stages each staff member may work, and a log of every
-- stage an item finished (who did it), which the production ledger and piece-rate pay (phase 5) read.

ALTER TABLE order_item
    ADD COLUMN assigned_staff_id BIGINT REFERENCES staff_account (id),
    ADD COLUMN assigned_at       TIMESTAMPTZ;

CREATE INDEX order_item_assigned ON order_item (assigned_staff_id) WHERE assigned_staff_id IS NOT NULL;

-- Stages a staff member may be given. Someone with no rows here may work every stage.
CREATE TABLE staff_stage (
    staff_id BIGINT NOT NULL REFERENCES staff_account (id) ON DELETE CASCADE,
    stage_id BIGINT NOT NULL REFERENCES production_stage (id) ON DELETE CASCADE,
    PRIMARY KEY (staff_id, stage_id)
);

-- One row each time an item leaves a stage. staff_id is who was assigned to it in that stage (null when nobody
-- was); actor is who moved it in the dashboard.
CREATE TABLE order_item_stage_log (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id       BIGINT       NOT NULL REFERENCES order_item (id) ON DELETE CASCADE,
    from_stage_id BIGINT REFERENCES production_stage (id),
    to_stage_id   BIGINT       NOT NULL REFERENCES production_stage (id),
    staff_id      BIGINT REFERENCES staff_account (id),
    actor         VARCHAR(120),
    moved_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX order_item_stage_log_item ON order_item_stage_log (item_id, moved_at);
CREATE INDEX order_item_stage_log_staff ON order_item_stage_log (staff_id, moved_at) WHERE staff_id IS NOT NULL;
