package dev.securebank.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  @org.springframework.core.annotation.Order(2)
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtDecoder decoder, ObjectMapper mapper, RateLimiter limiter)
      throws Exception {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
    return http.cors(c -> {})
        .csrf(c -> c.disable())
        .addFilterAfter(
            new SessionRateFilter(limiter, mapper),
            org.springframework.security.oauth2.server.resource.web.authentication
                .BearerTokenAuthenticationFilter.class)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/platform",
                        "/actuator/health",
                        "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/verify-email",
                        "/api/v1/auth/request-verification",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/mfa/verify")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/api/v1/fraud", "/api/v1/fraud/**")
                    .access(staff("ADMIN", "COMPLIANCE_OFFICER"))
                    .requestMatchers("/api/v1/admin/**", "/actuator/prometheus")
                    .access(staff("ADMIN", "SUPPORT_AGENT", "COMPLIANCE_OFFICER"))
                    .requestMatchers("/api/v1/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(
                        (req, res, error) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          mapper.writeValue(
                              res.getOutputStream(),
                              Map.of(
                                  "status",
                                  401,
                                  "code",
                                  "UNAUTHENTICATED",
                                  "message",
                                  "Sign in to continue.",
                                  "correlationId",
                                  String.valueOf(req.getAttribute("correlationId"))));
                        }))
        .exceptionHandling(
            e ->
                e.accessDeniedHandler(
                    (req, res, error) -> {
                      res.setStatus(403);
                      res.setContentType("application/json");
                      mapper.writeValue(
                          res.getOutputStream(),
                          Map.of(
                              "status",
                              403,
                              "code",
                              "FORBIDDEN",
                              "message",
                              "You do not have permission for this action.",
                              "correlationId",
                              String.valueOf(req.getAttribute("correlationId"))));
                    }))
        .headers(
            h ->
                h.addHeaderWriter(
                        new org.springframework.security.web.header.writers
                            .DelegatingRequestMatcherHeaderWriter(
                            request -> !request.getRequestURI().startsWith("/swagger-ui"),
                            new org.springframework.security.web.header.writers.StaticHeadersWriter(
                                "Content-Security-Policy",
                                "default-src 'none'; frame-ancestors 'none'")))
                    .addHeaderWriter(
                        new org.springframework.security.web.header.writers
                            .DelegatingRequestMatcherHeaderWriter(
                            request -> request.getRequestURI().startsWith("/swagger-ui"),
                            new org.springframework.security.web.header.writers.StaticHeadersWriter(
                                "Content-Security-Policy",
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'"))))
        .build();
  }

  @Bean
  @org.springframework.core.annotation.Order(1)
  SecurityFilterChain metricsChain(
      HttpSecurity http, ObjectMapper mapper, @Value("${securebank.metrics-token:}") String token)
      throws Exception {
    return http.securityMatcher("/actuator/prometheus")
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .addFilterBefore(
            new dev.securebank.monitoring.MetricsTokenFilter(token, mapper),
            org.springframework.security.web.access.intercept.AuthorizationFilter.class)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  private org.springframework.security.authorization.AuthorizationManager<
          org.springframework.security.web.access.intercept.RequestAuthorizationContext>
      staff(String... roles) {
    return (supplier, context) -> {
      var auth = supplier.get();
      boolean role =
          java.util.Arrays.stream(roles)
              .anyMatch(
                  value ->
                      auth.getAuthorities().stream()
                          .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + value)));
      boolean mfa =
          auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt token
              && Boolean.TRUE.equals(token.getClaimAsBoolean("mfa"));
      return new org.springframework.security.authorization.AuthorizationDecision(role && mfa);
    };
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${securebank.cors-origins}") List<String> origins) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(
        List.of(
            "Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-ID", "X-Device-ID"));
    config.setExposedHeaders(List.of("X-Correlation-ID", "Content-Disposition"));
    config.setAllowCredentials(false);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
