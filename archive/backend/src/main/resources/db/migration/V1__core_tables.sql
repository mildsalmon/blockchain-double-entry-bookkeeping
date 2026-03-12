CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,
    address VARCHAR(42) NOT NULL UNIQUE,
    label VARCHAR(255),
    sync_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    last_synced_at TIMESTAMP,
    last_synced_block BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(32) NOT NULL,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE price_cache (
    id BIGSERIAL PRIMARY KEY,
    token_address VARCHAR(42),
    token_symbol VARCHAR(64) NOT NULL,
    price_date DATE NOT NULL,
    price_krw NUMERIC(24, 8) NOT NULL,
    source VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (token_address, token_symbol, price_date, source)
);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(120) NOT NULL,
    action VARCHAR(64) NOT NULL,
    old_value JSONB,
    new_value JSONB,
    actor VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
