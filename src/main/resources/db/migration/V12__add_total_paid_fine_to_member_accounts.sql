-- Add total_paid_fine column to member_accounts
ALTER TABLE member_accounts ADD COLUMN IF NOT EXISTS total_paid_fine NUMERIC(15, 2) NOT NULL DEFAULT 0.00;

-- Backfill total_paid_fine from past financial transactions if any fine repayments exist
UPDATE member_accounts ma
SET total_paid_fine = COALESCE((
    SELECT SUM(t.amount)
    FROM financial_transactions t
    WHERE t.member_id = ma.member_id
      AND t.account_type = 'FINE'
      AND t.transaction_type = 'REPAYMENT'
      AND (t.is_reversed IS NULL OR t.is_reversed = false)
), 0.00)
WHERE ma.account_type = 'FINE';
