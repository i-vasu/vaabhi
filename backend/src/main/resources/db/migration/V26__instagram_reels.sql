-- The home page's Instagram reels: the latest few from the shop's account, each cover copied once to R2.
CREATE TABLE instagram_reel (
    media_id   text PRIMARY KEY,
    permalink  text        NOT NULL,
    caption    text,
    image_key  text        NOT NULL,
    posted_at  timestamptz NOT NULL,
    synced_at  timestamptz NOT NULL DEFAULT now()
);
