package dev.securebank.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.securebank.users.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.UUID;

@Configuration
public class JwtConfig {
    @Bean SecretKey jwtKey(@Value("${securebank.jwt-secret}") String secret) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET must encode at least 32 random bytes.");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key, UserRepository users) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> currentSession = jwt -> {
            try {
                var user = users.findById(UUID.fromString(jwt.getSubject())).orElseThrow();
                Number version = jwt.getClaim("ver");
                if (jwt.getAudience().contains("securebank-api") && version != null && version.intValue() == user.sessionVersion()
                        && user.status().equals("ACTIVE") && user.emailVerified() && user.role().equals(jwt.getClaimAsString("role"))) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (Exception ignored) { /* Invalid or revoked credentials fail closed. */ }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "The session is invalid or expired.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("securebank"), currentSession));
        return decoder;
    }
}
