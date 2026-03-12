CREATE TABLE journal_entries (
    id BIGSERIAL PRIMARY KEY,
    accounting_event_id BIGINT REFERENCES accounting_events(id),
    raw_transaction_id BIGINT REFERENCES raw_transactions(id),
    entry_date TIMESTAMP NOT NULL,
    description VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL,
    memo TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE journal_lines (
    id BIGSERIAL PRIMARY KEY,
    journal_entry_id BIGINT NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_code VARCHAR(120) NOT NULL REFERENCES accounts(code),
    debit_amount NUMERIC(24, 8) NOT NULL DEFAULT 0,
    credit_amount NUMERIC(24, 8) NOT NULL DEFAULT 0,
    token_symbol VARCHAR(20),
    token_quantity NUMERIC(38, 18),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE cost_basis_lots (
    id BIGSERIAL PRIMARY KEY,
    wallet_address VARCHAR(42) NOT NULL,
    token_symbol VARCHAR(20) NOT NULL,
    acquisition_date TIMESTAMP NOT NULL,
    quantity NUMERIC(38, 18) NOT NULL,
    remaining_qty NUMERIC(38, 18) NOT NULL,
    unit_cost_krw NUMERIC(24, 8) NOT NULL,
    raw_transaction_id BIGINT REFERENCES raw_transactions(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_journal_entries_entry_date_status ON journal_entries(entry_date, status);
CREATE INDEX idx_journal_lines_account_code ON journal_lines(account_code);
CREATE INDEX idx_cost_basis_wallet_token ON cost_basis_lots(wallet_address, token_symbol, acquisition_date);
