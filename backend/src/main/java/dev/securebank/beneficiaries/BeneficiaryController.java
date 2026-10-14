package dev.securebank.beneficiaries;

import dev.securebank.auth.Actor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/beneficiaries")
@PreAuthorize("hasRole('CUSTOMER')")
public class BeneficiaryController {
    private final BeneficiaryService beneficiaries;
    private final Actor actor;
    public BeneficiaryController(BeneficiaryService beneficiaries,Actor actor) { this.beneficiaries=beneficiaries; this.actor=actor; }
    @GetMapping public List<BeneficiaryService.BeneficiaryView> list() { return beneficiaries.list(actor.id()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public BeneficiaryService.BeneficiaryView add(@Valid @RequestBody BeneficiaryService.CreateBeneficiary input) { return beneficiaries.create(actor.id(),input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { beneficiaries.delete(actor.id(),id); }
}
