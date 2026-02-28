ALTER TABLE wallets
    ADD COLUMN sync_mode VARCHAR(32) NOT NULL DEFAULT 'FULL',
    ADD COLUMN sync_phase VARCHAR(32) NOT NULL DEFAULT 'NONE',
    ADD COLUMN cutoff_block BIGINT,
    ADD COLUMN snapshot_block BIGINT,
    ADD COLUMN delta_synced_block BIGINT;

CREATE TABLE wallet_tracked_tokens (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    token_address VARCHAR(42) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (wallet_id, token_address)
);

CREATE INDEX idx_wallet_tracked_tokens_wallet_id ON wallet_tracked_tokens(wallet_id);

CREATE TABLE wallet_balance_snapshots (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    token_address VARCHAR(42) NOT NULL,
    token_symbol VARCHAR(20) NOT NULL,
    balance_raw NUMERIC(78, 0) NOT NULL,
    cutoff_block BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (wallet_id, token_address)
);

CREATE INDEX idx_wallet_balance_snapshots_wallet_cutoff ON wallet_balance_snapshots(wallet_id, cutoff_block);
