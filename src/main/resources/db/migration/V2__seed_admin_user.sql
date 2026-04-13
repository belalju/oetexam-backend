-- Default admin user (password: admin123)
INSERT INTO users (email, password_hash, first_name, last_name, role, is_active)
VALUES ('admin@oetpractice.com', '$2a$12$LJ3m4ys3PzN1HsK1TBOiaeGFkmOdDJsMRiJq5D0JjSfGkxGNzJwvi', 'Admin', 'User', 'ADMIN', TRUE);
