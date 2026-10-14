package dev.securebank.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder, ObjectMapper mapper) throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
        return http.cors(c -> {}).csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/api/v1/platform", "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/verify-email", "/api/v1/auth/request-verification", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password", "/api/v1/auth/mfa/verify").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/fraud/**").hasAnyRole("ADMIN", "COMPLIANCE_OFFICER")
                .requestMatchers("/api/v1/admin/**", "/actuator/prometheus").hasAnyRole("ADMIN", "SUPPORT_AGENT", "COMPLIANCE_OFFICER")
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(converter))
                .authenticationEntryPoint((req,res,error) -> {
                    res.setStatus(401); res.setContentType("application/json");
                    mapper.writeValue(res.getOutputStream(), Map.of("status",401,"code","UNAUTHENTICATED","message","Sign in to continue.","correlationId",String.valueOf(req.getAttribute("correlationId"))));
                }))
            .exceptionHandling(e -> e.accessDeniedHandler((req,res,error) -> {
                res.setStatus(403); res.setContentType("application/json");
                mapper.writeValue(res.getOutputStream(), Map.of("status",403,"code","FORBIDDEN","message","You do not have permission for this action.","correlationId",String.valueOf(req.getAttribute("correlationId"))));
            }))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .build();
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${securebank.cors-origins}") List<String> origins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-ID", "X-Device-ID"));
        config.setExposedHeaders(List.of("X-Correlation-ID", "Content-Disposition"));
        config.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", config); return source;
    }
}
