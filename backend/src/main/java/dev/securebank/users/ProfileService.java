package dev.securebank.users;

import dev.securebank.audit.AuditService;
import dev.securebank.auth.AuthDtos.UserView;
import dev.securebank.common.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
  private final UserRepository users;
  private final AuditService audit;

  public ProfileService(UserRepository users, AuditService audit) {
    this.users = users;
    this.audit = audit;
  }

  public UserView me(UUID id) {
    return UserView.of(users.findById(id).orElseThrow(ApiException::notFound));
  }

  @Transactional
  public UserView rename(UUID id, String name) {
    if (name.trim().length() < 2)
      throw ApiException.badRequest("INVALID_NAME", "Enter at least two characters for your name.");
    User user = users.lockById(id).orElseThrow(ApiException::notFound);
    user.rename(name.trim());
    audit.record(id, "PROFILE_UPDATED", "SUCCESS", "users/" + id);
    return UserView.of(user);
  }
}
