-- Reference photos of a stock lot (the fabric, a close-up, the bill). Up to 6 per item, enforced by the service.
CREATE TABLE stock_item_photo (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stock_item_id BIGINT       NOT NULL REFERENCES stock_item (id) ON DELETE CASCADE,
    photo_key     VARCHAR(120) NOT NULL,
    position      INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX stock_item_photo_item ON stock_item_photo (stock_item_id, position);

-- A piece cut from stock that becomes one product. It moves through the workshop stages like an order item
-- until its product is published.
CREATE TABLE workshop_piece (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id        BIGINT       NOT NULL UNIQUE REFERENCES product (id) ON DELETE CASCADE,
    stock_item_id     BIGINT       NOT NULL REFERENCES stock_item (id),
    material_id       BIGINT       REFERENCES product_material (id) ON DELETE SET NULL,
    stage_id          BIGINT       NOT NULL REFERENCES production_stage (id),
    stage_since       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    assigned_staff_id BIGINT       REFERENCES staff_account (id),
    assigned_at       TIMESTAMPTZ,
    note              VARCHAR(200),
    created_by        VARCHAR(120),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX workshop_piece_stage ON workshop_piece (stage_id);

-- One row each time a piece leaves a stage, like order_item_stage_log.
CREATE TABLE workshop_piece_stage_log (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    piece_id      BIGINT       NOT NULL REFERENCES workshop_piece (id) ON DELETE CASCADE,
    from_stage_id BIGINT       REFERENCES production_stage (id),
    to_stage_id   BIGINT       NOT NULL REFERENCES production_stage (id),
    staff_id      BIGINT       REFERENCES staff_account (id),
    actor         VARCHAR(120),
    moved_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX workshop_piece_stage_log_piece ON workshop_piece_stage_log (piece_id, moved_at);

-- Design references for a piece (sketches, sample pictures), shared with the tailor. Up to 6 per piece.
CREATE TABLE workshop_piece_photo (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    piece_id   BIGINT       NOT NULL REFERENCES workshop_piece (id) ON DELETE CASCADE,
    photo_key  VARCHAR(120) NOT NULL,
    position   INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX workshop_piece_photo_piece ON workshop_piece_photo (piece_id, position);
