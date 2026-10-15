package dev.securebank.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

public class MetricsTokenFilter extends OncePerRequestFilter {
  private final String token;
  private final ObjectMapper mapper;

  public MetricsTokenFilter(String token, ObjectMapper mapper) {
    this.token = token;
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader("Authorization");
    if (token.length() < 32
        || supplied == null
        || !MessageDigest.isEqual(
            supplied.getBytes(StandardCharsets.UTF_8),
            ("Bearer " + token).getBytes(StandardCharsets.UTF_8))) {
      response.setStatus(403);
      response.setContentType("application/json");
      mapper.writeValue(
          response.getOutputStream(),
          Map.of(
              "status",
              403,
              "code",
              "FORBIDDEN",
              "message",
              "Metrics require the monitoring credential.",
              "correlationId",
              String.valueOf(request.getAttribute("correlationId"))));
      return;
    }
    chain.doFilter(request, response);
  }
}
