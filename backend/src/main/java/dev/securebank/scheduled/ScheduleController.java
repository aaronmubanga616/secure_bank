package dev.securebank.scheduled;

import dev.securebank.auth.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scheduled-transfers")
@PreAuthorize("hasRole('CUSTOMER')")
public class ScheduleController {
    public record ChangeSchedule(@jakarta.validation.constraints.NotNull @Pattern(regexp="ACTIVE|PAUSED|CANCELLED") String status) {}
    private final ScheduleService schedules;
    private final Actor actor;
    public ScheduleController(ScheduleService schedules,Actor actor) { this.schedules=schedules; this.actor=actor; }
    @GetMapping public List<ScheduleService.ScheduleView> list() { return schedules.list(actor.id()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ScheduleService.ScheduleView create(@Valid @RequestBody ScheduleService.CreateSchedule input) { return schedules.create(actor.id(),input); }
    @PatchMapping("/{id}") public ScheduleService.ScheduleView change(@PathVariable UUID id,@Valid @RequestBody ChangeSchedule input) { return schedules.change(actor.id(),id,input.status()); }
}
