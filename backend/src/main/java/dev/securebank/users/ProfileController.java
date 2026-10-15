package dev.securebank.users;

import dev.securebank.auth.Actor;
import dev.securebank.auth.AuthDtos.UserView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
public class ProfileController {
  public record UpdateProfile(@NotBlank @Size(min = 2, max = 100) String fullName) {}

  private final ProfileService profiles;
  private final Actor actor;

  public ProfileController(ProfileService profiles, Actor actor) {
    this.profiles = profiles;
    this.actor = actor;
  }

  @GetMapping
  public UserView me() {
    return profiles.me(actor.id());
  }

  @PatchMapping
  public UserView update(@Valid @RequestBody UpdateProfile input) {
    return profiles.rename(actor.id(), input.fullName());
  }
}
