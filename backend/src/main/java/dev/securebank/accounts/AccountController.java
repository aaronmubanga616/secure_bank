package dev.securebank.accounts;

import dev.securebank.auth.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import dev.securebank.auth.RateLimiter;
import java.time.Duration;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/accounts")
@PreAuthorize("hasRole('CUSTOMER')")
@Validated
public class AccountController {
    public record CreateAccount(@NotNull AccountService.Type type) {}
    private final AccountService accounts;
    private final Actor actor;
    private final RateLimiter limiter;
    private final dev.securebank.transactions.TransactionService transactions;
    public AccountController(AccountService accounts,Actor actor,RateLimiter limiter,dev.securebank.transactions.TransactionService transactions) { this.accounts=accounts; this.actor=actor; this.limiter=limiter; this.transactions=transactions; }
    @GetMapping("/lookup") public AccountService.Recipient lookup(@RequestParam @Pattern(regexp="[A-Za-z0-9]{8,20}") String number) {
        limiter.hit("recipient-lookup:"+actor.id(),20,Duration.ofMinutes(1)); return accounts.lookup(number);
    }
    @GetMapping public List<AccountService.AccountView> list() { return accounts.list(actor.id()); }
    @GetMapping("/{id}") public AccountService.AccountView get(@PathVariable UUID id) { return accounts.get(actor.id(),id); }
    @GetMapping("/{id}/transactions") public dev.securebank.common.PageResult<dev.securebank.transactions.TransactionDtos.TransactionView> history(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size) {return transactions.forAccount(actor.id(),id,page,size);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AccountService.AccountView create(@Valid @RequestBody CreateAccount input) { return accounts.create(actor.id(),input.type()); }
}
