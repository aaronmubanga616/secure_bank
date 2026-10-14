package dev.securebank.users;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "full_name", nullable = false, length = 100) private String fullName;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false, length = 32) private String role;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "email_verified", nullable = false) private boolean emailVerified;
    @Column(name = "mfa_enabled", nullable = false) private boolean mfaEnabled;
    @Column(name = "mfa_secret", columnDefinition = "text") private String mfaSecret;
    @Column(name = "session_version", nullable = false) private int sessionVersion;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected User() {}

    public static User register(String email, String name, String passwordHash) {
        User user = new User();
        user.id = UUID.randomUUID(); user.email = email; user.fullName = name;
        user.passwordHash = passwordHash; user.role = "CUSTOMER"; user.status = "ACTIVE";
        user.createdAt = Instant.now(); user.updatedAt = user.createdAt;
        return user;
    }

    public UUID id() { return id; }
    public String email() { return email; }
    public String fullName() { return fullName; }
    public String passwordHash() { return passwordHash; }
    public String role() { return role; }
    public String status() { return status; }
    public boolean emailVerified() { return emailVerified; }
    public boolean mfaEnabled() { return mfaEnabled; }
    public String mfaSecret() { return mfaSecret; }
    public int sessionVersion() { return sessionVersion; }
    public Instant createdAt() { return createdAt; }
    public void verifyEmail() { emailVerified = true; updatedAt = Instant.now(); }
    public void changePassword(String hash) { passwordHash = hash; invalidateSessions(); }
    public void invalidateSessions() { sessionVersion++; updatedAt = Instant.now(); }
    public void enableMfa(String encryptedSecret) { mfaSecret = encryptedSecret; mfaEnabled = true; invalidateSessions(); }
    public void disableMfa() { mfaSecret = null; mfaEnabled = false; invalidateSessions(); }
    public void rename(String name) { fullName = name; updatedAt = Instant.now(); }
}
