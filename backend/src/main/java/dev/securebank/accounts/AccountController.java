package dev.securebank.accounts;

import dev.securebank.auth.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@PreAuthorize("hasRole('CUSTOMER')")
public class AccountController {
    public record CreateAccount(@NotNull AccountService.Type type) {}
    private final AccountService accounts;
    private final Actor actor;
    public AccountController(AccountService accounts,Actor actor) { this.accounts=accounts; this.actor=actor; }
    @GetMapping public List<AccountService.AccountView> list() { return accounts.list(actor.id()); }
    @GetMapping("/{id}") public AccountService.AccountView get(@PathVariable UUID id) { return accounts.get(actor.id(),id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AccountService.AccountView create(@Valid @RequestBody CreateAccount input) { return accounts.create(actor.id(),input.type()); }
}
