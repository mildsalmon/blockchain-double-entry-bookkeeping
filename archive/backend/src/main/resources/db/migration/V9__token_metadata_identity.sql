CREATE TABLE token_metadata (
    id BIGSERIAL PRIMARY KEY,
    chain VARCHAR(32) NOT NULL,
    token_address VARCHAR(42) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    last_verified_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (chain, token_address)
);

ALTER TABLE journal_lines
    ADD COLUMN chain VARCHAR(32),
    ADD COLUMN token_address VARCHAR(42);

ALTER TABLE cost_basis_lots
    ADD COLUMN chain VARCHAR(32),
    ADD COLUMN token_address VARCHAR(42);

CREATE INDEX idx_token_metadata_chain_address ON token_metadata(chain, token_address);
CREATE INDEX idx_journal_lines_chain_address ON journal_lines(chain, token_address);
CREATE INDEX idx_cost_basis_wallet_chain_address ON cost_basis_lots(wallet_address, chain, token_address, acquisition_date);
