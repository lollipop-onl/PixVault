# --- !Ups

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    storage_quota BIGINT NOT NULL DEFAULT 107374182400,
    storage_used BIGINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_created_at ON users(created_at);

INSERT INTO users (id, email, password_hash, name, storage_quota, storage_used)
VALUES (
    '7f2a4b8e-1234-5678-90ab-cdef12345678'::uuid,
    'tanaka.yuki@example.com',
    '$2a$10$PTmY3pzONZSd8a9stZW1SuXwRKb1AUW.U5DQkLxM2qRh6GFm8uCTC',
    '田中 由紀',
    107374182400,
    45678901234
);

# --- !Downs

DROP TABLE IF EXISTS users;