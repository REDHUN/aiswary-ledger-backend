-- Drop total_paid_fine column from member_accounts
ALTER TABLE member_accounts DROP COLUMN IF EXISTS total_paid_fine;
