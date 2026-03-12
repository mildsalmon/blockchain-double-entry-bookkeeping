CREATE INDEX idx_audit_log_entity_lookup
    ON audit_log (entity_type, entity_id, action, created_at DESC);
