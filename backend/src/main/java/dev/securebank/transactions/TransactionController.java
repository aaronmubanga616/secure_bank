package dev.securebank.transactions;

import dev.securebank.auth.Actor;
import dev.securebank.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static dev.securebank.transactions.TransactionDtos.*;

@RestController
@RequestMapping("/api/v1/transactions")
@PreAuthorize("hasRole('CUSTOMER')")
@Validated
public class TransactionController {
    private final TransactionService transactions;
    private final Actor actor;
    public TransactionController(TransactionService transactions,Actor actor) { this.transactions=transactions; this.actor=actor; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public TransactionView create(@Valid @RequestBody CreateTransaction input,@RequestHeader("Idempotency-Key") @Size(min=8,max=128) String key) {
        return transactions.create(actor.id(),input,key);
    }
    @GetMapping("/{id}") public TransactionView get(@PathVariable UUID id) { return transactions.get(actor.id(),id); }
    @GetMapping public PageResult<TransactionView> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) @Size(max=100) String query,@RequestParam(required=false) Status status,
        @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,
        @RequestParam(required=false) BigDecimal minimum,@RequestParam(required=false) BigDecimal maximum) {
        return transactions.list(actor.id(),page,size,query,status,from,to,minimum,maximum);
    }
    @GetMapping("/limits") public Map<String,String> limits() {
        return Map.of("perTransaction","100000.00","dailyTransfer","200000.00","dailyWithdrawal","50000.00","dailyDeposit","200000.00");
    }
}
