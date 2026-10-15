package dev.securebank.auth;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class Actor {
  public UUID id() {
    return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
  }

  public boolean hasRole(String role) {
    return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
  }
}
