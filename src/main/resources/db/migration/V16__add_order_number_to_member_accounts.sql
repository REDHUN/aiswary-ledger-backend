-- Add order_number column to member_accounts table
ALTER TABLE member_accounts ADD COLUMN IF NOT EXISTS order_number INT NOT NULL DEFAULT 0;

-- Backfill order_number for existing member_accounts based on requested display order
UPDATE member_accounts
SET order_number = CASE account_type
    WHEN 'DEPOSIT' THEN 1
    WHEN 'LOAN' THEN 2
    WHEN 'SPECIAL_LOAN' THEN 3
    WHEN 'FINE' THEN 4
    WHEN 'TOTAL_PAID_FINE' THEN 5
    WHEN 'MONTHLY_CONTRIBUTION' THEN 6
    WHEN 'FINANCIAL_AID' THEN 7
    WHEN 'INTEREST' THEN 8
    WHEN 'GROUP_PROFIT' THEN 9
    WHEN 'GROUP_EXPENSE' THEN 10
    WHEN 'SURPLUS_FUND' THEN 11
    ELSE 99
END;

CREATE INDEX IF NOT EXISTS idx_member_accounts_order ON member_accounts(member_id, order_number);
