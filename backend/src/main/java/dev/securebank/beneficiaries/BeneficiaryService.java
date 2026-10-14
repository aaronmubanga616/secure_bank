package dev.securebank.beneficiaries;

import dev.securebank.audit.AuditService;
import dev.securebank.auth.AuthService;
import dev.securebank.common.ApiException;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BeneficiaryService {
    public record CreateBeneficiary(@NotBlank @Size(max=100) String name,@NotBlank @Pattern(regexp="[A-Za-z0-9]{8,20}") String accountNumber,
        @NotBlank @Size(max=72) String password,@Size(max=100) String code) {}
    public record BeneficiaryView(UUID id,String name,String accountNumber,String fullName,String status,Instant createdAt) {}
    private static final String SELECT="SELECT b.*,a.account_number,u.full_name FROM beneficiaries b JOIN accounts a ON a.id=b.destination_account JOIN users u ON u.id=a.user_id";
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final AuditService audit;
    public BeneficiaryService(JdbcTemplate jdbc,AuthService auth,AuditService audit) { this.jdbc=jdbc; this.auth=auth; this.audit=audit; }
    @Transactional public BeneficiaryView create(UUID owner,CreateBeneficiary input) {
        auth.stepUp(owner,input.password(),input.code());
        var target=jdbc.queryForList("SELECT a.id,a.user_id FROM accounts a JOIN users u ON u.id=a.user_id WHERE a.account_number=? AND a.status='ACTIVE' AND u.status='ACTIVE'",input.accountNumber().trim().toUpperCase(java.util.Locale.ROOT));
        if (target.isEmpty()) throw ApiException.badRequest("INVALID_RECIPIENT","Choose an active recipient account.");
        UUID destination=(UUID)target.getFirst().get("id");
        Integer count=jdbc.queryForObject("SELECT count(*) FROM beneficiaries WHERE user_id=? AND status='ACTIVE'",Integer.class,owner);
        if (count!=null && count>=100) throw ApiException.badRequest("BENEFICIARY_LIMIT","You can save up to 100 recipients.");
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM beneficiaries WHERE user_id=? AND destination_account=? AND status='ACTIVE')",Boolean.class,owner,destination)))
            throw new ApiException(HttpStatus.CONFLICT,"BENEFICIARY_EXISTS","This recipient is already saved.");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO beneficiaries(id,user_id,destination_account,name) VALUES (?,?,?,?)",id,owner,destination,input.name().trim());
        audit.record(owner,"BENEFICIARY_ADDED","SUCCESS","beneficiaries/"+id); return get(owner,id);
    }
    public List<BeneficiaryView> list(UUID owner) {
        return jdbc.query(SELECT+" WHERE b.user_id=? AND b.status='ACTIVE' ORDER BY b.name,b.id",(r,n)->new BeneficiaryView(r.getObject("id",UUID.class),r.getString("name"),r.getString("account_number"),r.getString("full_name"),r.getString("status"),r.getTimestamp("created_at").toInstant()),owner);
    }
    public BeneficiaryView get(UUID owner,UUID id) { return list(owner).stream().filter(b->b.id().equals(id)).findFirst().orElseThrow(ApiException::notFound); }
    @Transactional public void delete(UUID owner,UUID id) {
        if (jdbc.update("UPDATE beneficiaries SET status='DELETED' WHERE id=? AND user_id=? AND status='ACTIVE'",id,owner)!=1) throw ApiException.notFound();
        jdbc.update("UPDATE scheduled_transfers SET status='CANCELLED',updated_at=now() WHERE user_id=? AND beneficiary_id=? AND status IN ('ACTIVE','PAUSED')",owner,id);
        audit.record(owner,"BENEFICIARY_DELETED","SUCCESS","beneficiaries/"+id);
    }
}
