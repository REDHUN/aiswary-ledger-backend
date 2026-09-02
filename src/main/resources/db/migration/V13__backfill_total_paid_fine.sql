-- Ensure all members have a FINE account row
INSERT INTO member_accounts (member_id, account_type, current_balance, total_paid_fine, version, created_at, updated_at)
SELECT m.id, 'FINE', 0.00, 0.00, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM members m
WHERE NOT EXISTS (
    SELECT 1 FROM member_accounts ma WHERE ma.member_id = m.id AND ma.account_type = 'FINE'
);

-- Recalculate and update total_paid_fine for each member's FINE account from all past fine payments
UPDATE member_accounts ma
SET total_paid_fine = COALESCE((
    SELECT SUM(t.amount)
    FROM financial_transactions t
    WHERE t.member_id = ma.member_id
      AND t.account_type = 'FINE'
      AND (
          t.transaction_type = 'REPAYMENT'
          OR t.reference_type = 'MEETING_FINE_REPAYMENT'
          OR t.reference_type = 'FINE_REPAYMENT'
          OR (t.meeting_id IS NOT NULL AND (t.reference_type IS NULL OR t.reference_type != 'FINE_IMPOSED'))
      )
      AND (t.is_reversed IS NULL OR t.is_reversed = false)
), 0.00)
WHERE ma.account_type = 'FINE';
