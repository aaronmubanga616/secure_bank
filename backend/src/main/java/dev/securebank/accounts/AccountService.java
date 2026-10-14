package dev.securebank.accounts;

import dev.securebank.audit.AuditService;
import dev.securebank.common.ApiException;
import dev.securebank.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {
    public enum Type { SAVINGS, CURRENT }
    public record AccountView(UUID id,String accountNumber,String type,String currency,String status,Instant createdAt) {}
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final AuditService audit;
    public AccountService(JdbcTemplate jdbc,UserRepository users,AuditService audit) { this.jdbc=jdbc; this.users=users; this.audit=audit; }

    @Transactional
    public AccountView create(UUID owner,Type type) {
        var user=users.lockById(owner).orElseThrow(ApiException::notFound);
        if (!user.status().equals("ACTIVE") || !user.emailVerified() || !user.role().equals("CUSTOMER"))
            throw new ApiException(HttpStatus.FORBIDDEN,"ACCOUNT_INELIGIBLE","This user cannot open an account.");
        Integer count=jdbc.queryForObject("SELECT count(*) FROM accounts WHERE user_id=? AND type=?",Integer.class,owner,type.name());
        if (count!=null && count>0) throw new ApiException(HttpStatus.CONFLICT,"ACCOUNT_EXISTS","You already have this type of account.");
        UUID id=UUID.randomUUID();
        AccountView account=jdbc.queryForObject("INSERT INTO accounts(id,user_id,type) VALUES (?,?,?) RETURNING *",AccountService::view,id,owner,type.name());
        audit.record(owner,"ACCOUNT_CREATED","SUCCESS","accounts/"+id);
        return account;
    }

    public List<AccountView> list(UUID owner) {
        return jdbc.query("SELECT * FROM accounts WHERE user_id=? ORDER BY created_at,id",AccountService::view,owner);
    }

    public AccountView get(UUID owner,UUID id) {
        var accounts=jdbc.query("SELECT * FROM accounts WHERE user_id=? AND id=?",AccountService::view,owner,id);
        if (accounts.isEmpty()) throw ApiException.notFound();
        return accounts.getFirst();
    }

    private static AccountView view(ResultSet row,int index) throws SQLException {
        return new AccountView(row.getObject("id",UUID.class),row.getString("account_number"),row.getString("type"),
            row.getString("currency"),row.getString("status"),row.getTimestamp("created_at").toInstant());
    }
}
