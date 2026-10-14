package dev.securebank.scheduled;

import dev.securebank.accounts.AccountService;
import dev.securebank.audit.AuditService;
import dev.securebank.auth.AuthService;
import dev.securebank.beneficiaries.BeneficiaryService;
import dev.securebank.common.ApiException;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import static dev.securebank.scheduled.Recurrence.*;

@Service
public class ScheduleService {
    private static final String SELECT="SELECT s.*,b.name,a.account_number FROM scheduled_transfers s JOIN beneficiaries b ON b.id=s.beneficiary_id JOIN accounts a ON a.id=b.destination_account";
    public record CreateSchedule(@NotNull UUID sourceAccount,@NotNull UUID beneficiaryId,
        @NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal amount,@NotBlank @Size(max=160) String description,
        @NotNull Frequency frequency,@NotNull Instant startsAt,@NotBlank @Size(max=72) String password,@Size(max=100) String code) {}
    public record ScheduleView(UUID id,UUID sourceAccount,UUID beneficiaryId,String beneficiaryName,String accountNumber,String amount,String description,
        String frequency,Instant nextRunAt,String status,String lastResult,String lastError,int attempts,Instant createdAt) {}
    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final BeneficiaryService beneficiaries;
    private final AuthService auth;
    private final AuditService audit;
    public ScheduleService(JdbcTemplate jdbc,AccountService accounts,BeneficiaryService beneficiaries,AuthService auth,AuditService audit) {
        this.jdbc=jdbc; this.accounts=accounts; this.beneficiaries=beneficiaries; this.auth=auth; this.audit=audit;
    }
    @Transactional public ScheduleView create(UUID owner,CreateSchedule input) {
        auth.stepUp(owner,input.password(),input.code());
        var source=accounts.get(owner,input.sourceAccount()); var beneficiary=beneficiaries.get(owner,input.beneficiaryId());
        if (source.accountNumber().equals(beneficiary.accountNumber())) throw ApiException.badRequest("SAME_ACCOUNT","Choose different source and destination accounts.");
        if (!source.status().equals("ACTIVE")) throw ApiException.badRequest("ACCOUNT_UNAVAILABLE","Choose an active source account.");
        Instant now=Instant.now();
        if (input.startsAt().isBefore(now) || input.startsAt().isAfter(now.plus(Duration.ofDays(366)))) throw ApiException.badRequest("INVALID_DATE","Choose a start time within the next year.");
        BigDecimal amount;
        try { amount=input.amount().setScale(2,RoundingMode.UNNECESSARY); if (amount.signum()<=0 || amount.precision()>18) throw new ArithmeticException(); }
        catch(ArithmeticException error) { throw ApiException.badRequest("INVALID_AMOUNT","Use a positive amount with at most two decimal places."); }
        if (input.description().isBlank() || input.description().length()>160 || input.description().chars().anyMatch(Character::isISOControl)) throw ApiException.badRequest("INVALID_DESCRIPTION","Use a short, single-line description.");
        Integer count=jdbc.queryForObject("SELECT count(*) FROM scheduled_transfers WHERE user_id=? AND status IN ('ACTIVE','PAUSED')",Integer.class,owner);
        if (count!=null && count>=100) throw ApiException.badRequest("SCHEDULE_LIMIT","You can keep up to 100 active or paused schedules.");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO scheduled_transfers(id,user_id,source_account,beneficiary_id,amount,description,frequency,anchor_day,next_run_at) VALUES (?,?,?,?,?,?,?,?,?)",
            id,owner,input.sourceAccount(),input.beneficiaryId(),amount,input.description().trim(),input.frequency().name(),input.startsAt().atZone(ZONE).getDayOfMonth(),Timestamp.from(input.startsAt()));
        audit.record(owner,"SCHEDULE_CREATED","SUCCESS","scheduled-transfers/"+id); return get(owner,id);
    }
    public List<ScheduleView> list(UUID owner) {
        return jdbc.query(SELECT+" WHERE s.user_id=? ORDER BY CASE WHEN s.status IN ('ACTIVE','PAUSED') THEN 0 ELSE 1 END,s.created_at DESC LIMIT 200",ScheduleService::view,owner);
    }
    public ScheduleView get(UUID owner,UUID id) {
        var rows=jdbc.query(SELECT+" WHERE s.user_id=? AND s.id=?",ScheduleService::view,owner,id);
        if (rows.isEmpty()) throw ApiException.notFound(); return rows.getFirst();
    }
    private static ScheduleView view(ResultSet r,int index) throws SQLException {
        return new ScheduleView(r.getObject("id",UUID.class),r.getObject("source_account",UUID.class),r.getObject("beneficiary_id",UUID.class),r.getString("name"),r.getString("account_number"),r.getBigDecimal("amount").setScale(2).toPlainString(),
            r.getString("description"),r.getString("frequency"),r.getTimestamp("next_run_at").toInstant(),r.getString("status"),r.getString("last_result"),r.getString("last_error"),r.getInt("attempts"),r.getTimestamp("created_at").toInstant());
    }
    @Transactional public ScheduleView change(UUID owner,UUID id,String status) {
        if (!List.of("ACTIVE","PAUSED","CANCELLED").contains(status)) throw ApiException.badRequest("INVALID_STATUS","Choose ACTIVE, PAUSED, or CANCELLED.");
        var rows=jdbc.queryForList("SELECT status FROM scheduled_transfers WHERE id=? AND user_id=? FOR UPDATE",id,owner);
        if (rows.isEmpty()) throw ApiException.notFound();
        if (!List.of("ACTIVE","PAUSED").contains(rows.getFirst().get("status"))) throw new ApiException(HttpStatus.CONFLICT,"SCHEDULE_FINISHED","This schedule has already finished.");
        jdbc.update("UPDATE scheduled_transfers SET status=?,updated_at=now() WHERE id=?",status,id);
        audit.record(owner,"SCHEDULE_"+status,"SUCCESS","scheduled-transfers/"+id); return get(owner,id);
    }
}
