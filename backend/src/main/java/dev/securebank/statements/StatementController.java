package dev.securebank.statements;

import dev.securebank.auth.Actor;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/statements")
@PreAuthorize("hasRole('CUSTOMER')")
public class StatementController {
  private final StatementService statements;
  private final Actor actor;

  public StatementController(StatementService statements, Actor actor) {
    this.statements = statements;
    this.actor = actor;
  }

  @GetMapping(produces = "application/pdf")
  public ResponseEntity<byte[]> download(
      @RequestParam UUID accountId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=securebank-statement-" + from + ".pdf")
        .body(statements.generate(actor.id(), accountId, from, to));
  }
}
