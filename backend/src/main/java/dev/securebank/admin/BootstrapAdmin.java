package dev.securebank.admin;
import dev.securebank.users.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
@Component
@ConditionalOnProperty(name="securebank.bootstrap-enabled",havingValue="true")
public class BootstrapAdmin implements ApplicationRunner {
    private final JdbcTemplate jdbc; private final UserRepository users; private final PasswordEncoder passwords; private final String email; private final String password;
    public BootstrapAdmin(JdbcTemplate jdbc,UserRepository users,PasswordEncoder passwords,@Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,@Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {this.jdbc=jdbc;this.users=users;this.passwords=passwords;this.email=email.trim().toLowerCase(Locale.ROOT);this.password=password;}
    @Override @Transactional public void run(ApplicationArguments args) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended('bootstrap-admin',0))");
        if(jdbc.queryForObject("SELECT count(*) FROM users WHERE role='ADMIN'",Integer.class)>0) return;
        if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length()>254 || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Provide a valid bootstrap email and a password of 12+ characters, at most 72 UTF-8 bytes.");
        if(users.findByEmail(email).isPresent()) throw new IllegalArgumentException("Bootstrap email is already used. Choose another staff email.");
        users.saveAndFlush(User.staff(email,"SecureBank Administrator",passwords.encode(password),"ADMIN"));
    }
}
