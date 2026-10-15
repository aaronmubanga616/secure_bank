package dev.securebank.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "securebank.monitoring-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class BankMetrics {
  private final JdbcTemplate jdbc;
  private final Admin kafka;
  private final Map<String, AtomicLong> values = new HashMap<>();
  private final org.springframework.data.redis.core.StringRedisTemplate redis;

  public BankMetrics(
      JdbcTemplate jdbc,
      MeterRegistry registry,
      ObjectProvider<Admin> kafka,
      org.springframework.data.redis.core.StringRedisTemplate redis) {
    this.jdbc = jdbc;
    this.kafka = kafka.getIfAvailable();
    this.redis = redis;
    for (String name :
        List.of(
            "database.up",
            "redis.up",
            "kafka.up",
            "outbox.pending",
            "outbox.oldest.seconds",
            "mail.pending",
            "mail.dead",
            "fraud.open",
            "schedules.due",
            "snapshot.timestamp")) {
      var value = new AtomicLong();
      values.put(name, value);
      Gauge.builder("securebank." + name, value, AtomicLong::get).register(registry);
    }
    for (String status : List.of("COMPLETED", "FAILED", "FLAGGED")) {
      var value = new AtomicLong();
      values.put("transactions." + status, value);
      Gauge.builder("securebank.transactions", value, AtomicLong::get)
          .tag("status", status)
          .description("Current durable transaction count, sampled every fifteen seconds")
          .register(registry);
    }
    for (String group :
        List.of("securebank-audit", "securebank-notifications", "securebank-fraud")) {
      var value = new AtomicLong(-1);
      values.put(group, value);
      Gauge.builder("securebank.kafka.lag", value, AtomicLong::get)
          .tag("group", group)
          .description("Unconsumed records, or -1 when unavailable")
          .register(registry);
    }
  }

  @Scheduled(fixedDelayString = "${securebank.metrics-interval-ms:15000}")
  public void snapshot() {
    try {
      set("outbox.pending", count("outbox_events WHERE published_at IS NULL"));
      set(
          "outbox.oldest.seconds",
          jdbc.queryForObject(
              "SELECT coalesce(extract(epoch FROM now()-min(created_at)),0)::bigint FROM outbox_events WHERE published_at IS NULL",
              Long.class));
      set("mail.pending", count("mail_queue WHERE status='PENDING'"));
      set("mail.dead", count("mail_queue WHERE status='DEAD'"));
      set("fraud.open", count("fraud_alerts WHERE status='OPEN'"));
      set(
          "schedules.due",
          count("scheduled_transfers WHERE status='ACTIVE' AND next_run_at<=now()"));
      for (String status : List.of("COMPLETED", "FAILED", "FLAGGED"))
        set(
            "transactions." + status,
            jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE status=?", Long.class, status));
      set("snapshot.timestamp", System.currentTimeMillis() / 1000);
      set("database.up", 1L);
    } catch (org.springframework.dao.DataAccessException ignored) {
      set(
          "database.up",
          0L); /* A stale timestamp exposes failed sampling without leaking SQL or data. */
    }
    try {
      String pong =
          redis.execute(
              (org.springframework.data.redis.core.RedisCallback<String>)
                  connection -> connection.ping());
      set("redis.up", "PONG".equals(pong) ? 1L : 0L);
    } catch (Exception ignored) {
      set("redis.up", 0L);
    }
    if (kafka == null) return;
    try {
      kafka.describeCluster().nodes().get(2, TimeUnit.SECONDS);
      set("kafka.up", 1L);
    } catch (Exception error) {
      set("kafka.up", 0L);
    }
    for (String group : List.of("securebank-audit", "securebank-notifications", "securebank-fraud"))
      try {
        var committed =
            kafka
                .listConsumerGroupOffsets(group)
                .partitionsToOffsetAndMetadata()
                .get(3, TimeUnit.SECONDS);
        if (committed.isEmpty()) {
          set(group, -1L);
          continue;
        }
        var request = new HashMap<org.apache.kafka.common.TopicPartition, OffsetSpec>();
        committed.keySet().forEach(partition -> request.put(partition, OffsetSpec.latest()));
        var latest = kafka.listOffsets(request).all().get(3, TimeUnit.SECONDS);
        long lag = 0;
        for (var entry : committed.entrySet())
          lag += Math.max(0, latest.get(entry.getKey()).offset() - entry.getValue().offset());
        set(group, lag);
      } catch (Exception error) {
        set(group, -1L);
        if (error instanceof InterruptedException) {
          Thread.currentThread().interrupt();
          return;
        }
      }
  }

  private long count(String table) {
    Long count = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    return count == null ? 0 : count;
  }

  private void set(String name, Long value) {
    values.get(name).set(value == null ? 0 : value);
  }
}
