package dev.securebank.auth;

import dev.securebank.audit.AuditService;
import dev.securebank.common.ApiException;
import dev.securebank.notifications.MailQueue;
import dev.securebank.events.EventService;
import dev.securebank.users.User;
import dev.securebank.users.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static dev.securebank.auth.AuthDtos.*;

@Service
public class AuthService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwt;
    private final StringRedisTemplate redis;
    private final RateLimiter limiter;
    private final Totp totp;
    private final Crypto crypto;
    private final AuditService audit;
    private final EventService events;
    private final MailQueue mail;
    private final int accessMinutes;
    private final int refreshDays;
    private final String webUrl;
    private final String dummyHash;

    public AuthService(UserRepository users, JdbcTemplate jdbc, PasswordEncoder passwords, JwtEncoder jwt,
            StringRedisTemplate redis, RateLimiter limiter, Totp totp, Crypto crypto, AuditService audit,
            EventService events, MailQueue mail, @Value("${securebank.access-token-minutes}") int accessMinutes,
            @Value("${securebank.refresh-token-days}") int refreshDays, @Value("${securebank.web-url}") String webUrl) {
        this.users=users; this.jdbc=jdbc; this.passwords=passwords; this.jwt=jwt; this.redis=redis; this.limiter=limiter;
        this.totp=totp; this.crypto=crypto; this.audit=audit; this.events=events; this.accessMinutes=accessMinutes;
        this.refreshDays=refreshDays; this.webUrl=webUrl; this.mail=mail; this.dummyHash=passwords.encode(Crypto.token());
    }

    @Transactional
    public void register(Register input) {
        validatePassword(input.password());
        String email=normalize(input.email());
        // A database advisory lock also serializes registration for the same email across API instances.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "register:"+email);
        if (users.findByEmail(email).isPresent()) { matches(input.password(), dummyHash); return; }
        User user=users.saveAndFlush(User.register(email,input.fullName().trim(),passwords.encode(input.password())));
        sendVerification(user,"VERIFY_EMAIL");
        audit.record(user.id(),"USER_REGISTERED","SUCCESS","users/"+user.id());
        events.record("UserRegistered",user.id(),"users/"+user.id(),Map.of());
    }

    @Transactional(noRollbackFor=ApiException.class)
    public AuthResult login(Login input) {
        String email=normalize(input.email());
        limiter.hit("login:"+email,10,Duration.ofMinutes(15));
        User user=users.lockByEmail(email).orElse(null);
        boolean valid=matches(input.password(),user==null?dummyHash:user.passwordHash());
        if (!valid || user==null || !user.status().equals("ACTIVE")) {
            audit.record(user==null?null:user.id(),"LOGIN","FAILURE","authentication"); throw unauthenticated();
        }
        if (!user.emailVerified()) throw new ApiException(HttpStatus.FORBIDDEN,"EMAIL_UNVERIFIED","Verify your email before signing in.");
        if (user.mfaEnabled()) {
            String challenge=Crypto.token();
            redis.opsForValue().set("mfa:challenge:"+Crypto.hash(challenge),user.id()+":"+user.sessionVersion(),Duration.ofMinutes(5));
            return new AuthResult(null,null,null,true,challenge);
        }
        audit.record(user.id(),"LOGIN","SUCCESS","authentication");
        return session(user,UUID.randomUUID());
    }

    @Transactional(noRollbackFor=ApiException.class)
    public AuthResult verifyMfa(MfaVerify input) {
        limiter.hit("mfa-challenge:"+input.challenge(),5,Duration.ofMinutes(5));
        String key="mfa:challenge:"+Crypto.hash(input.challenge());
        String id=redis.opsForValue().get(key);
        if (id==null) throw unauthenticated();
        String[] parts=id.split(":",2);
        User user=users.lockById(UUID.fromString(parts[0])).orElseThrow(AuthService::unauthenticated);
        if(parts.length!=2 || !parts[1].equals(Integer.toString(user.sessionVersion()))) throw unauthenticated();
        if (!user.mfaEnabled() || !user.status().equals("ACTIVE") || !user.emailVerified()) throw unauthenticated();
        requireMfa(user,input.code());
        // Delete atomically after validation; concurrent consumers cannot both issue a session.
        if (!Boolean.TRUE.equals(redis.delete(key))) throw unauthenticated();
        audit.record(user.id(),"MFA_LOGIN","SUCCESS","authentication");
        return session(user,UUID.randomUUID());
    }

    @Transactional(noRollbackFor=ApiException.class)
    public AuthResult refresh(String value) {
        String hash=Crypto.hash(value);
        var candidate=jdbc.queryForList("SELECT user_id FROM refresh_tokens WHERE token_hash=?",hash);
        if (candidate.isEmpty()) throw unauthenticated();
        UUID userId=(UUID)candidate.getFirst().get("user_id");
        // Always lock the user before a token row; password reset and refresh use the same lock order.
        User user=users.lockById(userId).orElseThrow(AuthService::unauthenticated);
        var rows=jdbc.queryForList("SELECT * FROM refresh_tokens WHERE token_hash=? FOR UPDATE",hash);
        var row=rows.getFirst(); UUID family=(UUID)row.get("family_id");
        if ((Boolean)row.get("revoked")) {
            jdbc.update("UPDATE refresh_tokens SET revoked=true WHERE family_id=?",family);
            user.invalidateSessions(); users.flush();
            audit.record(userId,"REFRESH_REPLAY","BLOCKED","authentication"); throw unauthenticated();
        }
        if (((Timestamp)row.get("expires_at")).toInstant().isBefore(Instant.now()) || !user.status().equals("ACTIVE") || !user.emailVerified()) throw unauthenticated();
        jdbc.update("UPDATE refresh_tokens SET revoked=true WHERE token_hash=?",hash);
        return session(user,family);
    }

    @Transactional
    public void logout(String token) {
        var rows=jdbc.queryForList("SELECT user_id,family_id FROM refresh_tokens WHERE token_hash=?",Crypto.hash(token));
        if (rows.isEmpty()) return;
        User user=users.lockById((UUID)rows.getFirst().get("user_id")).orElseThrow(AuthService::unauthenticated);
        jdbc.update("UPDATE refresh_tokens SET revoked=true WHERE family_id=?",rows.getFirst().get("family_id"));
        user.invalidateSessions();
        audit.record(user.id(),"LOGOUT","SUCCESS","authentication");
    }

    @Transactional
    public void requestVerification(String email,String purpose) {
        limiter.hit("security-email:"+purpose+":"+normalize(email),3,Duration.ofMinutes(15));
        users.findByEmail(normalize(email)).filter(u->u.status().equals("ACTIVE"))
            .filter(u->purpose.equals("RESET_PASSWORD") || !u.emailVerified()).ifPresent(u->sendVerification(u,purpose));
    }

    @Transactional
    public void verifyEmail(String token) {
        UUID id=consumeToken(token,"VERIFY_EMAIL");
        User user=users.lockById(id).orElseThrow(AuthService::unauthenticated); user.verifyEmail();
        audit.record(id,"EMAIL_VERIFIED","SUCCESS","users/"+id);
    }

    @Transactional
    public void resetPassword(ResetPassword input) {
        validatePassword(input.password());
        UUID id=consumeToken(input.token(),"RESET_PASSWORD");
        User user=users.lockById(id).orElseThrow(AuthService::unauthenticated);
        user.changePassword(passwords.encode(input.password()));
        jdbc.update("UPDATE refresh_tokens SET revoked=true WHERE user_id=?",id);
        jdbc.update("DELETE FROM verification_tokens WHERE user_id=? AND purpose='RESET_PASSWORD'",id);
        audit.record(id,"PASSWORD_RESET","SUCCESS","users/"+id);
        mail.enqueue(user.email(),"SecureBank password changed","Your simulator password was changed. If this was unexpected, contact your simulator administrator.");
    }

    @Transactional
    public void changePassword(UUID id,ChangePassword input) {
        validatePassword(input.newPassword()); User user=users.lockById(id).orElseThrow(AuthService::unauthenticated);
        if (!matches(input.currentPassword(),user.passwordHash())) throw unauthenticated();
        user.changePassword(passwords.encode(input.newPassword()));
        jdbc.update("UPDATE refresh_tokens SET revoked=true WHERE user_id=?",id);
        audit.record(id,"PASSWORD_CHANGED","SUCCESS","users/"+id);
        mail.enqueue(user.email(),"SecureBank password changed","Your simulator password was changed.");
    }

    public MfaSetup setupMfa(UUID id,String password) {
        User user=users.findById(id).orElseThrow(AuthService::unauthenticated);
        limiter.hit("mfa-setup:"+id,5,Duration.ofMinutes(10));
        if (!matches(password,user.passwordHash())) throw unauthenticated();
        if (user.mfaEnabled()) throw ApiException.badRequest("MFA_ENABLED","MFA is already enabled.");
        String secret=totp.secret();
        redis.opsForValue().set("mfa:setup:"+id,crypto.encrypt(secret),Duration.ofMinutes(10));
        String label=URLEncoder.encode("SecureBank:"+user.email(),StandardCharsets.UTF_8);
        return new MfaSetup(secret,"otpauth://totp/"+label+"?secret="+secret+"&issuer=SecureBank&algorithm=SHA1&digits=6&period=30");
    }

    @Transactional
    public void stepUp(UUID id,String password,String code) {
        limiter.hit("step-up:"+id,10,Duration.ofMinutes(5));
        User user=users.lockById(id).orElseThrow(AuthService::unauthenticated);
        if (!user.status().equals("ACTIVE") || !user.emailVerified()) throw new ApiException(HttpStatus.FORBIDDEN,"ACCOUNT_INELIGIBLE","This user cannot perform this action.");
        if (password==null || password.getBytes(StandardCharsets.UTF_8).length>72 || !matches(password,user.passwordHash()))
            throw new ApiException(HttpStatus.FORBIDDEN,"VERIFICATION_FAILED","Check your password and security code.");
        if (user.mfaEnabled()) {
            try { requireMfa(user,code==null?"":code); }
            catch(ApiException error) {
                if (error.status()==HttpStatus.UNAUTHORIZED) throw new ApiException(HttpStatus.FORBIDDEN,"VERIFICATION_FAILED","Check your password and security code.");
                throw error;
            }
        }
    }

    @Transactional
    public RecoveryCodes confirmMfa(UUID id,String code) {
        limiter.hit("mfa-confirm:"+id,5,Duration.ofMinutes(10));
        User user=users.lockById(id).orElseThrow(AuthService::unauthenticated);
        String encrypted=redis.opsForValue().get("mfa:setup:"+id);
        if (encrypted==null || user.mfaEnabled() || totp.validStep(crypto.decrypt(encrypted),code,Instant.now())<0) throw unauthenticated();
        user.enableMfa(encrypted); redis.delete("mfa:setup:"+id);
        var codes=new ArrayList<String>();
        for (int i=0;i<8;i++) {
            String recovery=Crypto.token().substring(0,20); codes.add(recovery);
            jdbc.update("INSERT INTO mfa_recovery_codes(user_id,code_hash) VALUES (?,?)",id,Crypto.hash(recovery));
        }
        jdbc.update("UPDATE refresh_tokens SET revoked=true WHERE user_id=?",id);
        audit.record(id,"MFA_ENABLED","SUCCESS","users/"+id);
        return new RecoveryCodes(codes);
    }

    @Transactional
    public void disableMfa(UUID id,DisableMfa input) {
        limiter.hit("mfa-disable:"+id,5,Duration.ofMinutes(10));
        User user=users.lockById(id).orElseThrow(AuthService::unauthenticated);
        if (!user.mfaEnabled() || !matches(input.password(),user.passwordHash())) throw unauthenticated();
        requireMfa(user,input.code()); user.disableMfa();
        jdbc.update("DELETE FROM mfa_recovery_codes WHERE user_id=?",id);
        jdbc.update("UPDATE refresh_tokens SET revoked=true WHERE user_id=?",id);
        audit.record(id,"MFA_DISABLED","SUCCESS","users/"+id);
    }

    private void requireMfa(User user,String code) {
        limiter.hit("mfa-user:"+user.id(),10,Duration.ofMinutes(5));
        if (code.matches("[0-9]{6}")) {
            long step=totp.validStep(crypto.decrypt(user.mfaSecret()),code,Instant.now());
            if (step>=0 && Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("mfa:used:"+user.id()+":"+step,"1",Duration.ofMinutes(2)))) return;
        } else if (jdbc.update("DELETE FROM mfa_recovery_codes WHERE user_id=? AND code_hash=?",user.id(),Crypto.hash(code))==1) return;
        audit.record(user.id(),"MFA_VERIFY","FAILURE","authentication"); throw unauthenticated();
    }

    private AuthResult session(User user,UUID family) {
        Instant now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer("securebank").subject(user.id().toString()).audience(List.of("securebank-api"))
            .issuedAt(now).expiresAt(now.plus(Duration.ofMinutes(accessMinutes))).id(UUID.randomUUID().toString())
            .claim("role",user.role()).claim("ver",user.sessionVersion()).claim("mfa",user.mfaEnabled()).build();
        String access=jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        String refresh=Crypto.token();
        jdbc.update("INSERT INTO refresh_tokens(id,user_id,token_hash,family_id,expires_at) VALUES (?,?,?,?,?)",
            UUID.randomUUID(),user.id(),Crypto.hash(refresh),family,Timestamp.from(now.plus(Duration.ofDays(refreshDays))));
        return new AuthResult(access,refresh,UserView.of(user),false,null);
    }

    @Transactional public void rememberDevice(AuthResult result,String device) {
        if(result.user()==null || device==null || !device.matches("[A-Za-z0-9_-]{16,100}")) return;
        jdbc.update("INSERT INTO user_devices(user_id,device_hash) VALUES (?,?) ON CONFLICT(user_id,device_hash) DO UPDATE SET last_seen_at=now()",result.user().id(),Crypto.hash(device));
    }

    private void sendVerification(User user,String purpose) {
        String token=Crypto.token();
        jdbc.update("DELETE FROM verification_tokens WHERE user_id=? AND purpose=?",user.id(),purpose);
        jdbc.update("INSERT INTO verification_tokens(token_hash,user_id,purpose,expires_at) VALUES (?,?,?,?)",
            Crypto.hash(token),user.id(),purpose,Timestamp.from(Instant.now().plus(purpose.equals("VERIFY_EMAIL")?Duration.ofHours(24):Duration.ofMinutes(30))));
        String path=purpose.equals("VERIFY_EMAIL")?"verify-email":"reset-password";
        // Fragments do not enter browser request logs or Referer headers.
        mail.enqueue(user.email(),purpose.equals("VERIFY_EMAIL")?"Verify your SecureBank email":"Reset your SecureBank password",
            "SecureBank educational simulator\n\nOpen "+webUrl+"/"+path+"#token="+token+"\n\nIf you did not request this, ignore this email.");
    }

    private UUID consumeToken(String value,String purpose) {
        var rows=jdbc.queryForList("DELETE FROM verification_tokens WHERE token_hash=? AND purpose=? AND expires_at>now() RETURNING user_id",Crypto.hash(value),purpose);
        if (rows.isEmpty()) throw ApiException.badRequest("INVALID_TOKEN","This link is invalid or expired. Request a new one.");
        return (UUID)rows.getFirst().get("user_id");
    }

    private void validatePassword(String password) {
        int length=password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length()<12 || length>72) throw ApiException.badRequest("PASSWORD_POLICY","Use at least 12 characters and no more than 72 UTF-8 bytes.");
    }
    private boolean matches(String raw,String hash) { return raw!=null && raw.getBytes(StandardCharsets.UTF_8).length<=72 && passwords.matches(raw,hash); }
    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private static ApiException unauthenticated() { return new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","The credentials are invalid or expired."); }
}

