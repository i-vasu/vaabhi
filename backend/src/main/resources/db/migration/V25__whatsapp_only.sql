-- Customer messages go out on WhatsApp only (SMS is not used), so a message no longer has a channel.
ALTER TABLE message_log DROP COLUMN channel;

-- The message templates no longer choose a channel.
UPDATE shop_setting SET value = value - 'channel' WHERE section = 'messages';
