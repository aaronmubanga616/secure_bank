package dev.securebank.admin;

import dev.securebank.accounts.AccountService;
import dev.securebank.audit.AuditService;
import dev.securebank.auth.AuthDtos.UserView;
import dev.securebank.common.*;
import dev.securebank.events.EventService;
import dev.securebank.users.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Service
public class StaffService {
    public record StaffUser(UUID id,String email,String fullName,String role,String status,boolean emailVerified,boolean mfaEnabled,Instant createdAt) {}
    public record CustomerDetail(StaffUser user,List<AccountService.AccountView> accounts) {}
    public record ChangeStatus(String status,String reason) {}
    public record NewStaff(String email,String fullName,String role,String password) {}
    public record AuditView(UUID id,UUID userId,String action,String result,String resource,String correlationId,Instant createdAt) {}
    private final JdbcTemplate jdbc; private final AccountService accounts; private final AuditService audit; private final EventService events; private final UserRepository users; private final PasswordEncoder passwords;
    public StaffService(JdbcTemplate jdbc,AccountService accounts,AuditService audit,EventService events,UserRepository users,PasswordEncoder passwords) {this.jdbc=jdbc;this.accounts=accounts;this.audit=audit;this.events=events;this.users=users;this.passwords=passwords;}
    public PageResult<StaffUser> search(String query,String status,int page,int size) {
        page(page,size); if(query!=null&&query.length()>100 || status!=null&&!Set.of("ACTIVE","SUSPENDED").contains(status)) throw ApiException.badRequest("INVALID_FILTER","Check the customer filters.");
        String pattern="%"+(query==null?"":query.trim())+"%";
        String where=" WHERE role='CUSTOMER' AND (email ILIKE ? OR full_name ILIKE ? OR id::text ILIKE ?) AND (?::text IS NULL OR status=?)";
        Long total=jdbc.queryForObject("SELECT count(*) FROM users"+where,Long.class,pattern,pattern,pattern,status,status);
        var content=jdbc.query("SELECT * FROM users"+where+" ORDER BY created_at DESC,id LIMIT ? OFFSET ?",StaffService::user,pattern,pattern,pattern,status,status,size,page*size);
        return new PageResult<>(content,page,size,total==null?0:total);
    }
    @Transactional public CustomerDetail detail(UUID actor,UUID id) {
        var rows=jdbc.query("SELECT * FROM users WHERE id=? AND role='CUSTOMER'",StaffService::user,id);
        if(rows.isEmpty())throw ApiException.notFound();audit.record(actor,"CUSTOMER_VIEWED","SUCCESS","users/"+id);return new CustomerDetail(rows.getFirst(),accounts.list(id));
    }
    @Transactional public void userStatus(UUID actor,UUID id,ChangeStatus input) {
        reason(input.reason());if(!Set.of("ACTIVE","SUSPENDED").contains(input.status()==null?"":input.status()))throw ApiException.badRequest("INVALID_STATUS","Choose ACTIVE or SUSPENDED.");
        var rows=jdbc.queryForList("SELECT * FROM users WHERE id=? AND role='CUSTOMER' FOR UPDATE",id);
        if(rows.isEmpty())throw ApiException.notFound();
        jdbc.update("UPDATE users SET status=?,session_version=session_version+1,updated_at=now() WHERE id=?",input.status(),id);
        jdbc.update("UPDATE refresh_tokens SET revoked=true WHERE user_id=?",id);
        if(input.status().equals("SUSPENDED"))jdbc.update("UPDATE scheduled_transfers SET status='PAUSED',updated_at=now() WHERE user_id=? AND status='ACTIVE'",id);
        action(actor,"USER_"+input.status(),"users/"+id,input.reason());
        events.record("NotificationRequested",id,"users/"+id,Map.of("title","Account access updated","message","Your simulator access status has changed. Contact your simulator administrator for details.","category","SECURITY"));
    }
    @Transactional public void accountStatus(UUID actor,UUID id,ChangeStatus input) {
        reason(input.reason());if(!Set.of("ACTIVE","FROZEN").contains(input.status()==null?"":input.status()))throw ApiException.badRequest("INVALID_STATUS","Choose ACTIVE or FROZEN.");
        var rows=jdbc.queryForList("SELECT user_id FROM accounts WHERE id=?",id);if(rows.isEmpty())throw ApiException.notFound();UUID owner=(UUID)rows.getFirst().get("user_id");
        jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE",owner);
        jdbc.queryForList("SELECT id FROM accounts WHERE id=? FOR UPDATE",id);
        jdbc.update("UPDATE accounts SET status=? WHERE id=?",input.status(),id);
        action(actor,"ACCOUNT_"+input.status(),"accounts/"+id,input.reason());
        events.record("NotificationRequested",owner,"accounts/"+id,Map.of("title","Account "+input.status().toLowerCase(Locale.ROOT),"message","A staff member updated your simulator account status.","category","SECURITY"));
    }
    @Transactional public UserView createStaff(UUID actor,NewStaff input) {
        if(input.email()==null||!input.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||input.email().length()>254||input.fullName()==null||input.fullName().trim().length()<2||input.fullName().length()>100||input.role()==null||!Set.of("ADMIN","SUPPORT_AGENT","COMPLIANCE_OFFICER").contains(input.role())||input.password()==null||input.password().length()<12||input.password().getBytes(StandardCharsets.UTF_8).length>72)throw ApiException.badRequest("INVALID_STAFF","Check the email, name, role, and password (12+ characters, at most 72 bytes).");
        String email=input.email().trim().toLowerCase(Locale.ROOT);jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","register:"+email);
        if(users.findByEmail(email).isPresent())throw new ApiException(HttpStatus.CONFLICT,"EMAIL_EXISTS","This email is already used.");
        var user=users.saveAndFlush(User.staff(email,input.fullName().trim(),passwords.encode(input.password()),input.role()));audit.record(actor,"STAFF_CREATED","SUCCESS","users/"+user.id());return UserView.of(user);
    }
    public PageResult<AuditView> audit(String query,int page,int size) {
        page(page,size);if(query!=null&&query.length()>100)throw ApiException.badRequest("INVALID_FILTER","Use a shorter search.");String pattern="%"+(query==null?"":query)+"%";
        String where=" WHERE action ILIKE ? OR resource ILIKE ? OR correlation_id ILIKE ?";
        Long total=jdbc.queryForObject("SELECT count(*) FROM audit_logs"+where,Long.class,pattern,pattern,pattern);
        var rows=jdbc.query("SELECT * FROM audit_logs"+where+" ORDER BY created_at DESC,id LIMIT ? OFFSET ?",(r,n)->new AuditView(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getString("action"),r.getString("result"),r.getString("resource"),r.getString("correlation_id"),r.getTimestamp("created_at").toInstant()),pattern,pattern,pattern,size,page*size);return new PageResult<>(rows,page,size,total==null?0:total);
    }
    @Transactional public void changeRules(UUID actor,Map<String,Integer> input) {
        Integer amount=input.get("largeAmount"),velocity=input.get("velocityCount"),hold=input.get("holdScore");if(amount==null||amount<1||amount>1000000000||velocity==null||velocity<2||velocity>100||hold==null||hold<31||hold>100)throw ApiException.badRequest("INVALID_RULE","Check the fraud rule thresholds.");
        jdbc.update("UPDATE fraud_rules SET large_amount=?,velocity_count=?,hold_score=? WHERE id=1",amount,velocity,hold);audit.record(actor,"FRAUD_RULES_CHANGED","SUCCESS","fraud-rules");
    }
    public Map<String,Object> rules() {return jdbc.queryForMap("SELECT large_amount::text AS \"largeAmount\",velocity_count AS \"velocityCount\",hold_score AS \"holdScore\" FROM fraud_rules WHERE id=1");}
    private void action(UUID actor,String action,String resource,String reason) {jdbc.update("INSERT INTO staff_actions(id,actor_id,resource,action,reason) VALUES (?,?,?,?,?)",UUID.randomUUID(),actor,resource,action,reason.trim());audit.record(actor,action,"SUCCESS",resource);}
    private static void reason(String reason) {if(reason==null||reason.trim().length()<10||reason.length()>500||reason.chars().anyMatch(Character::isISOControl))throw ApiException.badRequest("REASON_REQUIRED","Provide a reason of 10–500 characters.");}
    private static void page(int page,int size) {if(page<0||page>100000||size<1||size>100)throw ApiException.badRequest("INVALID_PAGE","Choose a page size from 1 to 100.");}
    private static StaffUser user(ResultSet r,int n)throws SQLException{return new StaffUser(r.getObject("id",UUID.class),r.getString("email"),r.getString("full_name"),r.getString("role"),r.getString("status"),r.getBoolean("email_verified"),r.getBoolean("mfa_enabled"),r.getTimestamp("created_at").toInstant());}
}
