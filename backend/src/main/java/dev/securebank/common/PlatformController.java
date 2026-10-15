package dev.securebank.common;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformController {
  @GetMapping("/api/v1/platform")
  public Map<String, String> info() {
    return Map.of("name", "SecureBank", "mode", "EDUCATIONAL_SIMULATOR", "currency", "ZMW");
  }
}
