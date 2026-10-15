package dev.securebank.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.securebank.common.ApiException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionRateFilter extends OncePerRequestFilter {
  private final RateLimiter limiter;
  private final ObjectMapper mapper;

  public SessionRateFilter(RateLimiter limiter, ObjectMapper mapper) {
    this.limiter = limiter;
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (request.getRequestURI().startsWith("/api/v1/")
        && authentication != null
        && authentication.isAuthenticated())
      try {
        limiter.hit("session-api:" + authentication.getName(), 120, Duration.ofMinutes(1));
      } catch (ApiException error) {
        response.setStatus(error.status().value());
        response.setContentType("application/json");
        if (error.status().value() == 429) response.setHeader("Retry-After", "60");
        mapper.writeValue(
            response.getOutputStream(),
            Map.of(
                "status",
                error.status().value(),
                "code",
                error.code(),
                "message",
                error.getMessage(),
                "correlationId",
                String.valueOf(request.getAttribute("correlationId"))));
        return;
      }
    chain.doFilter(request, response);
  }
}
