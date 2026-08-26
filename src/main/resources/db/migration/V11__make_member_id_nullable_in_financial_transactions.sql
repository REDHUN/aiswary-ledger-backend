-- Make member_id nullable in financial_transactions for group-level transactions like Surplus Fund additions
ALTER TABLE financial_transactions ALTER COLUMN member_id DROP NOT NULL;
