package dev.securebank.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CachedStatistics {
  private final JdbcTemplate jdbc;
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;

  public CachedStatistics(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.redis = redis;
    this.mapper = mapper;
  }

  public Map<String, Object> snapshot() {
    try {
      String cached = redis.opsForValue().get("cache:staff-statistics:v1");
      if (cached != null)
        return mapper.readValue(cached, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ignored) {
      /* The non-authoritative display cache can fail open to PostgreSQL. */
    }
    var result =
        new LinkedHashMap<String, Object>(
            jdbc.queryForMap(
                "SELECT count(*) AS transactions,count(*) FILTER(WHERE status='COMPLETED') AS successful,count(*) FILTER(WHERE status='FAILED') AS failed,count(*) FILTER(WHERE status='FLAGGED') AS flagged,coalesce(sum(amount) FILTER(WHERE status='COMPLETED'),0)::numeric(30,2)::text AS \"transactionVolume\" FROM transactions"));
    result.putAll(
        jdbc.queryForMap(
            "SELECT count(*) AS customers,count(*) FILTER(WHERE status='ACTIVE') AS \"activeCustomers\" FROM users WHERE role='CUSTOMER'"));
    result.put("accounts", jdbc.queryForObject("SELECT count(*) FROM accounts", Long.class));
    result.put("statsAsOf", Instant.now().toString());
    try {
      redis
          .opsForValue()
          .set(
              "cache:staff-statistics:v1",
              mapper.writeValueAsString(result),
              Duration.ofSeconds(5));
    } catch (Exception ignored) {
      /* Never affect banking decisions because the display cache is unavailable. */
    }
    return result;
  }
}
