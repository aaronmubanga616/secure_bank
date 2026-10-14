package dev.securebank.notifications;
import dev.securebank.auth.Actor;
import dev.securebank.common.PageResult;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications; private final Actor actor;
    public NotificationController(NotificationService notifications,Actor actor) { this.notifications=notifications; this.actor=actor; }
    @GetMapping public PageResult<NotificationService.NotificationView> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return notifications.list(actor.id(),page,size); }
    @PatchMapping("/{id}/read") public void read(@PathVariable UUID id) { notifications.markRead(actor.id(),id); }
    @PostMapping("/read-all") public void readAll() { notifications.markAllRead(actor.id()); }
}
