-- =============================================================================
-- V2__seed_admin.sql  –  Bootstrap ADMIN user for development / first login
--
-- Password: admin123
-- Hash generated with: new BCryptPasswordEncoder(10).encode("admin123")
-- To regenerate:  echo '{"password":"admin123"}' and run the /auth/register
-- or use Spring Shell: new BCryptPasswordEncoder(10).encode("admin123")
-- =============================================================================

INSERT INTO users (username, email, full_name, role, password_hash, created_at, updated_at)
VALUES (
    'admin',
    'admin@issueflow.local',
    'System Administrator',
    'ADMIN',
    '$2a$10$OHM/7Jdjt0k2zq5tOUhoc.8W2tr5uk9LRC3KjkS7fvj18p/.EmMKi',
    NOW(),
    NOW()
);
