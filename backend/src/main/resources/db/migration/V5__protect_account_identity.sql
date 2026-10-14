CREATE FUNCTION protect_account_identity() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id<>OLD.id OR NEW.user_id<>OLD.user_id OR NEW.account_number<>OLD.account_number OR NEW.type<>OLD.type OR NEW.currency<>OLD.currency THEN
        RAISE EXCEPTION 'Account identity cannot be changed' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER account_identity BEFORE UPDATE ON accounts FOR EACH ROW EXECUTE FUNCTION protect_account_identity();
CREATE TRIGGER ledger_account_identity BEFORE UPDATE OR DELETE ON ledger_accounts FOR EACH ROW EXECUTE FUNCTION reject_journal_mutation();
