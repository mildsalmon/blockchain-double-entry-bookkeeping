CREATE TABLE accounting_events (
    id BIGSERIAL PRIMARY KEY,
    raw_transaction_id BIGINT NOT NULL REFERENCES raw_transactions(id),
    event_type VARCHAR(50) NOT NULL,
    classifier_id VARCHAR(100) NOT NULL,
    token_address VARCHAR(42),
    token_symbol VARCHAR(20),
    amount_raw NUMERIC(78, 0) NOT NULL,
    amount_decimal NUMERIC(38, 18) NOT NULL,
    counterparty VARCHAR(42),
    price_krw NUMERIC(24, 8),
    price_source VARCHAR(50),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounting_events_raw_tx_id ON accounting_events(raw_transaction_id);
CREATE INDEX idx_accounting_events_event_type ON accounting_events(event_type);
