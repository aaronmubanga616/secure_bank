package dev.securebank.admin;

import dev.securebank.audit.AuditService;
import dev.securebank.auth.Actor;
import dev.securebank.auth.AuthDtos.UserView;
import dev.securebank.common.PageResult;
import dev.securebank.transactions.*;
import dev.securebank.transactions.TransactionDtos.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN','SUPPORT_AGENT','COMPLIANCE_OFFICER')")
public class StaffController {
  private final StaffService staff;
  private final TransactionService transactions;
  private final JdbcTemplate jdbc;
  private final Actor actor;
  private final AuditService audit;
  private final HealthEndpoint health;
  private final MeterRegistry meters;
  private final CachedStatistics statistics;

  public StaffController(
      StaffService staff,
      TransactionService transactions,
      JdbcTemplate jdbc,
      Actor actor,
      AuditService audit,
      HealthEndpoint health,
      MeterRegistry meters,
      CachedStatistics statistics) {
    this.staff = staff;
    this.transactions = transactions;
    this.jdbc = jdbc;
    this.actor = actor;
    this.audit = audit;
    this.health = health;
    this.meters = meters;
    this.statistics = statistics;
  }

  @GetMapping("/users")
  public PageResult<StaffService.StaffUser> users(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return staff.search(query, status, page, size);
  }

  @GetMapping("/users/{id}")
  public StaffService.CustomerDetail user(@PathVariable UUID id) {
    return staff.detail(actor.id(), id);
  }

  @PatchMapping("/users/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  public void userStatus(@PathVariable UUID id, @RequestBody StaffService.ChangeStatus input) {
    staff.userStatus(actor.id(), id, input);
  }

  @PatchMapping("/accounts/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  public void accountStatus(@PathVariable UUID id, @RequestBody StaffService.ChangeStatus input) {
    staff.accountStatus(actor.id(), id, input);
  }

  @PostMapping("/staff")
  @PreAuthorize("hasRole('ADMIN')")
  public UserView create(@RequestBody StaffService.NewStaff input) {
    return staff.createStaff(actor.id(), input);
  }

  @GetMapping("/transactions")
  public PageResult<TransactionView> transactions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Status status,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) BigDecimal minimum,
      @RequestParam(required = false) BigDecimal maximum) {
    if (query != null && query.length() > 100)
      throw dev.securebank.common.ApiException.badRequest(
          "INVALID_FILTER", "Use a shorter search.");
    return transactions.listAll(page, size, query, status, from, to, minimum, maximum);
  }

  @GetMapping("/transactions/{id}")
  @Transactional
  public Map<String, Object> transaction(@PathVariable UUID id) {
    var tx = transactions.forStaff(id);
    audit.record(actor.id(), "TRANSACTION_VIEWED", "SUCCESS", "transactions/" + id);
    return Map.of(
        "transaction",
        tx,
        "customerId",
        jdbc.queryForObject("SELECT user_id FROM transactions WHERE id=?", UUID.class, id),
        "journal",
        jdbc.queryForList(
            "SELECT e.direction,e.amount::text AS amount,l.code FROM ledger_entries e JOIN ledger_accounts l ON l.id=e.ledger_account_id WHERE transaction_id=? ORDER BY direction",
            id));
  }

  @GetMapping("/audit")
  public PageResult<StaffService.AuditView> audit(
      @RequestParam(required = false) String query,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return staff.audit(query, page, size);
  }

  @GetMapping("/actions")
  public List<Map<String, Object>> actions(@RequestParam String resource) {
    if (resource.length() > 160)
      throw dev.securebank.common.ApiException.badRequest(
          "INVALID_FILTER", "Use a shorter resource.");
    return jdbc.queryForList(
        "SELECT actor_id,action,reason,created_at::text AS created_at FROM staff_actions WHERE resource=? ORDER BY created_at DESC LIMIT 100",
        resource);
  }

  @GetMapping("/fraud-rules")
  @PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE_OFFICER')")
  public Map<String, Object> rules() {
    return staff.rules();
  }

  @PatchMapping("/fraud-rules")
  @PreAuthorize("hasRole('ADMIN')")
  public void rules(@RequestBody Map<String, Integer> input) {
    staff.changeRules(actor.id(), input);
  }

  @GetMapping("/system")
  public Map<String, Object> system() {
    var result = new LinkedHashMap<String, Object>(statistics.snapshot());
    result.put("status", health.health().getStatus().getCode());
    result.put("openAlerts", count("fraud_alerts WHERE status='OPEN'"));
    result.put("pendingEvents", count("outbox_events WHERE published_at IS NULL"));
    result.put("deadEmails", count("mail_queue WHERE status='DEAD'"));
    result.put(
        "usedHeapBytes",
        (long)
            meters.find("jvm.memory.used").gauges().stream()
                .mapToDouble(gauge -> gauge.value())
                .sum());
    result.put("timestamp", Instant.now());
    return result;
  }

  private Long count(String expression) {
    return jdbc.queryForObject("SELECT count(*) FROM " + expression, Long.class);
  }
}
