-- Cancelling a paid online order no longer waits on Razorpay: the order is cancelled first and the refund is sent
-- after the commit. When Razorpay refuses it, refund_status is FAILED and refund_error keeps Razorpay's reason, so
-- the owner can send it again from the order.
ALTER TABLE web_order
    ADD COLUMN refund_error VARCHAR(200);
