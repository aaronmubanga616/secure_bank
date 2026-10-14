package dev.securebank.admin;

import dev.securebank.auth.Actor;
import dev.securebank.transactions.LimitService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/limits")
public class LimitController {
    private final LimitService limits;
    private final Actor actor;
    public LimitController(LimitService limits,Actor actor) { this.limits=limits; this.actor=actor; }
    @GetMapping public Map<String,String> get() { return limits.current().view(); }
    @PatchMapping @PreAuthorize("hasRole('ADMIN')")
    public Map<String,String> update(@Valid @RequestBody LimitService.Limits input) { return limits.update(actor.id(),input); }
}
