package dev.securebank.scheduled;

import dev.securebank.audit.AuditService;
import dev.securebank.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class ScheduleExecutor {
    private final JdbcTemplate jdbc;
    private final ScheduledPaymentRunner runner;
    private final AuditService audit;
    public ScheduleExecutor(JdbcTemplate jdbc,ScheduledPaymentRunner runner,AuditService audit) { this.jdbc=jdbc; this.runner=runner; this.audit=audit; }
    @Transactional public void execute(UUID id) {
        var rows=jdbc.queryForList("SELECT s.*,a.account_number,b.status AS beneficiary_status FROM scheduled_transfers s JOIN beneficiaries b ON b.id=s.beneficiary_id JOIN accounts a ON a.id=b.destination_account WHERE s.id=? AND s.status='ACTIVE' AND s.next_run_at<=now() AND (s.next_attempt_at IS NULL OR s.next_attempt_at<=now()) FOR UPDATE OF s SKIP LOCKED",id);
        if (rows.isEmpty()) return;
        var row=rows.getFirst(); UUID owner=(UUID)row.get("user_id"); Instant due=((Timestamp)row.get("next_run_at")).toInstant();
        if (!row.get("beneficiary_status").equals("ACTIVE")) { jdbc.update("UPDATE scheduled_transfers SET status='CANCELLED',updated_at=now() WHERE id=?",id); return; }
        try {
            // A separate money transaction survives worker crashes; its fixed key makes recovery safe.
            var transaction=runner.pay(owner,id,due,(UUID)row.get("source_account"),(String)row.get("account_number"),(BigDecimal)row.get("amount"),(String)row.get("description"));
            jdbc.update("INSERT INTO scheduled_occurrences(schedule_id,due_at,transaction_id,status) VALUES (?,?,?,?) ON CONFLICT(schedule_id,due_at) DO NOTHING",id,Timestamp.from(due),transaction.id(),transaction.status());
            var frequency=Recurrence.Frequency.valueOf((String)row.get("frequency"));
            Instant next=Recurrence.next(due,frequency,((Number)row.get("anchor_day")).intValue());
            while(next!=null && !next.isAfter(Instant.now())) next=Recurrence.next(next,frequency,((Number)row.get("anchor_day")).intValue());
            String state=next==null?(transaction.status().equals("FAILED")?"FAILED":"COMPLETED"):"ACTIVE";
            jdbc.update("UPDATE scheduled_transfers SET status=?,next_run_at=?,next_attempt_at=NULL,attempts=0,last_result=?,last_error=?,updated_at=now() WHERE id=?",
                state,Timestamp.from(next==null?due:next),transaction.status(),transaction.failureReason(),id);
            audit.record(owner,"SCHEDULE_EXECUTED",transaction.status(),"scheduled-transfers/"+id);
        } catch(Exception error) {
            int attempts=((Number)row.get("attempts")).intValue()+1;
            boolean permanent=error instanceof ApiException api && api.status().is4xxClientError();
            String reason=error instanceof ApiException api?api.code():"TEMPORARY_FAILURE";
            jdbc.update("UPDATE scheduled_transfers SET attempts=?,status=?,next_attempt_at=now()+(? * interval '1 second'),last_error=?,updated_at=now() WHERE id=?",
                attempts,permanent || attempts>=3?"FAILED":"ACTIVE",15L<<(Math.min(attempts,5)-1),reason,id);
            audit.record(owner,"SCHEDULE_ATTEMPT","FAILED","scheduled-transfers/"+id);
        }
    }
}
