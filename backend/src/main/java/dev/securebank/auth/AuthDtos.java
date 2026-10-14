package dev.securebank.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.securebank.users.User;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}
    public record Register(@Email @NotBlank @Size(max=254) String email, @NotBlank @Size(min=2,max=100) String fullName, @NotBlank @Size(min=12,max=72) String password) {}
    public record Login(@Email @NotBlank @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
    public record EmailRequest(@Email @NotBlank @Size(max=254) String email) {}
    public record TokenRequest(@NotBlank @Size(max=200) String token) {}
    public record RefreshRequest(@NotBlank @Size(max=200) String refreshToken) {}
    public record ResetPassword(@NotBlank @Size(max=200) String token, @NotBlank @Size(min=12,max=72) String password) {}
    public record ChangePassword(@NotBlank @Size(max=72) String currentPassword, @NotBlank @Size(min=12,max=72) String newPassword) {}
    public record MfaVerify(@NotBlank @Size(max=200) String challenge, @NotBlank @Size(max=100) String code) {}
    public record PasswordRequest(@NotBlank @Size(max=72) String password) {}
    public record MfaCode(@NotBlank @Size(max=100) String code) {}
    public record DisableMfa(@NotBlank @Size(max=72) String password, @NotBlank @Size(max=100) String code) {}
    public record UserView(UUID id, String email, String fullName, String role, String status, boolean emailVerified, boolean mfaEnabled) {
        public static UserView of(User user) { return new UserView(user.id(), user.email(), user.fullName(), user.role(), user.status(), user.emailVerified(), user.mfaEnabled()); }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthResult(String accessToken, String refreshToken, UserView user, boolean mfaRequired, String challenge) {}
    public record MfaSetup(String secret, String otpauthUrl) {}
    public record RecoveryCodes(List<String> recoveryCodes) {}
}
