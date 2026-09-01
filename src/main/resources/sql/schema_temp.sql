-- 用户表
CREATE TABLE IF NOT EXISTS users
(
    id                   BIGSERIAL PRIMARY KEY,
    user_code            VARCHAR(64)  NOT NULL,
    username             VARCHAR(64)  NOT NULL,
    email                VARCHAR(128) NOT NULL,
    display_name         VARCHAR(128) NOT NULL,
    password_hash        VARCHAR(256) NOT NULL,
    system_role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    status               VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at        TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- Refresh Token 表
CREATE TABLE IF NOT EXISTS user_refresh_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_id   VARCHAR(64)  NOT NULL,
    token_hash VARCHAR(256) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_token_id
    ON user_refresh_tokens (token_id);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user_active
    ON user_refresh_tokens (user_id, revoked_at, expires_at);