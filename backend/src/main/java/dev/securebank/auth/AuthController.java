package dev.securebank.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Map;
import static dev.securebank.auth.AuthDtos.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final RateLimiter limiter;
    private final Actor actor;
    public AuthController(AuthService auth,RateLimiter limiter,Actor actor) { this.auth=auth; this.limiter=limiter; this.actor=actor; }
    private void limit(HttpServletRequest request) { limiter.hit("auth-ip:"+request.getRemoteAddr(),60,Duration.ofMinutes(1)); }
    private Map<String,String> accepted() { return Map.of("message","If the account is eligible, an email has been sent."); }

    @PostMapping("/register") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,String> register(@Valid @RequestBody Register input,HttpServletRequest request) { limit(request); auth.register(input); return accepted(); }
    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody Login input,HttpServletRequest request) { limit(request); return auth.login(input); }
    @PostMapping("/refresh")
    public AuthResult refresh(@Valid @RequestBody RefreshRequest input,HttpServletRequest request) { limit(request); return auth.refresh(input.refreshToken()); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest input,HttpServletRequest request) { limit(request); auth.logout(input.refreshToken()); }
    @PostMapping("/verify-email") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody TokenRequest input,HttpServletRequest request) { limit(request); auth.verifyEmail(input.token()); }
    @PostMapping("/request-verification") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,String> requestVerification(@Valid @RequestBody EmailRequest input,HttpServletRequest request) { limit(request); auth.requestVerification(input.email(),"VERIFY_EMAIL"); return accepted(); }
    @PostMapping("/forgot-password") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,String> forgotPassword(@Valid @RequestBody EmailRequest input,HttpServletRequest request) { limit(request); auth.requestVerification(input.email(),"RESET_PASSWORD"); return accepted(); }
    @PostMapping("/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPassword input,HttpServletRequest request) { limit(request); auth.resetPassword(input); }
    @PostMapping("/change-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePassword input) { auth.changePassword(actor.id(),input); }
    @PostMapping("/mfa/verify")
    public AuthResult verifyMfa(@Valid @RequestBody MfaVerify input,HttpServletRequest request) { limit(request); return auth.verifyMfa(input); }
    @PostMapping("/mfa/setup")
    public MfaSetup setup(@Valid @RequestBody PasswordRequest input) { return auth.setupMfa(actor.id(),input.password()); }
    @PostMapping("/mfa/confirm")
    public RecoveryCodes confirm(@Valid @RequestBody MfaCode input) { return auth.confirmMfa(actor.id(),input.code()); }
    @PostMapping("/mfa/disable") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@Valid @RequestBody DisableMfa input) { auth.disableMfa(actor.id(),input); }
}
