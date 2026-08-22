-- V2__seed_data.sql
-- Seed initial Admin user (username: admin, password: admin123) and Member users (username: john / anu, password: admin123)

INSERT INTO users (username, password_hash, role, is_active, created_at, updated_at) VALUES
('admin', '$2a$10$Lq6iJHgPzEQOW0FS8fYlkugFAcnyJlhvh9zupiqszfaLqEuVNcIoW', 'ADMIN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
