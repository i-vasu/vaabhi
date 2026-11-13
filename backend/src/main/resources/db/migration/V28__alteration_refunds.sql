-- Cancelling an alteration after money was collected now asks whether the money goes back or the shop keeps it,
-- as shop orders already do. A refund is a negative alteration_payment, so the ledger shows it going out.
ALTER TABLE alteration_payment DROP CONSTRAINT IF EXISTS alteration_payment_amount_rupees_check;
ALTER TABLE alteration_payment
    ADD CONSTRAINT alteration_payment_amount_rupees_check CHECK (amount_rupees <> 0);

-- NONE: nothing was collected; REFUNDED: given back; KEPT: the shop kept it (e.g. work already done).
ALTER TABLE alteration
    ADD COLUMN cancel_settlement VARCHAR(8) CHECK (cancel_settlement IN ('NONE', 'REFUNDED', 'KEPT'));
