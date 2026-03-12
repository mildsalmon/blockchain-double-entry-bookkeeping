CREATE TABLE raw_transactions (
    id BIGSERIAL PRIMARY KEY,
    wallet_address VARCHAR(42) NOT NULL,
    tx_hash VARCHAR(66) NOT NULL UNIQUE,
    block_number BIGINT NOT NULL,
    tx_index INTEGER,
    block_timestamp TIMESTAMP NOT NULL,
    raw_data JSONB NOT NULL,
    tx_status SMALLINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_raw_transactions_wallet_address ON raw_transactions(wallet_address);
CREATE INDEX idx_raw_transactions_block_number ON raw_transactions(block_number, tx_index);
