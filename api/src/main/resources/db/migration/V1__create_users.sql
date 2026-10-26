CREATE TABLE users (
                       id            UUID         PRIMARY KEY,
                       name          VARCHAR(100) NOT NULL,
                       email         VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(100) NOT NULL,
                       role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
                       created_at    TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX uk_users_email ON users (email);
