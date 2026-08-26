-- Drop check constraints on account_type and transaction_type for H2 and PostgreSQL
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS financial_transactions_account_type_check;
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS financial_transactions_transaction_type_check;
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS CONSTRAINT_DF0;
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS CONSTRAINT_DF1;
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS CONSTRAINT_DF2;
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS CONSTRAINT_DF3;
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS CONSTRAINT_DF4;
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS CONSTRAINT_DF5;
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS CONSTRAINT_DF6;
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS CONSTRAINT_DF7;
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS CONSTRAINT_DF07;
ALTER TABLE financial_transactions DROP CONSTRAINT IF EXISTS CONSTRAINT_DF8;
