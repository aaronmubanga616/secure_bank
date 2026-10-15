CREATE TABLE staff_actions (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES users(id),
    resource VARCHAR(160) NOT NULL,
    action VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER audit_immutable BEFORE UPDATE OR DELETE ON audit_logs FOR EACH ROW EXECUTE FUNCTION reject_journal_mutation();
CREATE TRIGGER audit_no_truncate BEFORE TRUNCATE ON audit_logs FOR EACH STATEMENT EXECUTE FUNCTION reject_journal_mutation();
CREATE TRIGGER staff_action_immutable BEFORE UPDATE OR DELETE ON staff_actions FOR EACH ROW EXECUTE FUNCTION reject_journal_mutation();
