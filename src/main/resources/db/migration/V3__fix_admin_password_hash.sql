-- Password: admin123
-- Hash generated with: new BCryptPasswordEncoder(10).encode("admin123")
UPDATE users
SET password_hash = '$2a$10$OHM/7Jdjt0k2zq5tOUhoc.8W2tr5uk9LRC3KjkS7fvj18p/.EmMKi',
    updated_at = NOW()
WHERE username = 'admin';
