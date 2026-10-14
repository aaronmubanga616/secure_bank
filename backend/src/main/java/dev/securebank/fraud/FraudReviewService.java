package dev.securebank.fraud;

import dev.securebank.audit.AuditService;
import dev.securebank.common.ApiException;
import dev.securebank.transactions.TransactionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class FraudReviewService {
    public record Review(String outcome,String note) {}
    private final JdbcTemplate jdbc;
    private final TransactionService transactions;
    private final AuditService audit;
    public FraudReviewService(JdbcTemplate jdbc,TransactionService transactions,AuditService audit) { this.jdbc=jdbc; this.transactions=transactions; this.audit=audit; }
    public List<Map<String,Object>> list(String status) {
        if(status!=null && !Set.of("OPEN","RESOLVED","FALSE_POSITIVE","CONFIRMED_FRAUD").contains(status)) throw ApiException.badRequest("INVALID_STATUS","Choose a review status.");
        return jdbc.queryForList("SELECT f.*,u.full_name,u.email,t.reference,t.amount::text,t.status AS transaction_status FROM fraud_alerts f JOIN users u ON u.id=f.user_id LEFT JOIN transactions t ON t.id=f.transaction_id WHERE (?::text IS NULL OR f.status=?) ORDER BY f.created_at DESC LIMIT 200",status,status);
    }
    @Transactional public void review(UUID actor,UUID id,Review input) {
        if(input.outcome()==null || !Set.of("RESOLVED","FALSE_POSITIVE","CONFIRMED_FRAUD").contains(input.outcome()) || input.note()==null || input.note().trim().length()<10 || input.note().length()>500) throw ApiException.badRequest("INVALID_REVIEW","Choose an outcome and provide a note of 10–500 characters.");
        // Lock the alert before its transaction. Concurrent reviewers can never release a hold twice.
        var rows=jdbc.queryForList("SELECT * FROM fraud_alerts WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw ApiException.notFound();
        var row=rows.getFirst(); if(!row.get("status").equals("OPEN")) throw ApiException.badRequest("ALREADY_REVIEWED","This alert has already been reviewed.");
        UUID transaction=(UUID)row.get("transaction_id");
        if(transaction!=null) transactions.reviewHeld(transaction,!input.outcome().equals("CONFIRMED_FRAUD"));
        jdbc.update("UPDATE fraud_alerts SET status=?,review_note=?,reviewed_by=?,reviewed_at=now() WHERE id=?",input.outcome(),input.note().trim(),actor,id);
        audit.record(actor,"FRAUD_"+input.outcome(),"SUCCESS","fraud/"+id);
    }
}
