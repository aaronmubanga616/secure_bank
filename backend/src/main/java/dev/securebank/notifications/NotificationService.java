package dev.securebank.notifications;

import dev.securebank.common.ApiException;
import dev.securebank.common.PageResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
  public record NotificationView(
      UUID id, String title, String message, String category, boolean read, Instant createdAt) {}

  private final JdbcTemplate jdbc;

  public NotificationService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void add(UUID user, UUID event, String title, String message, String category) {
    jdbc.update(
        "INSERT INTO notifications(id,user_id,event_id,title,message,category) VALUES (?,?,?,?,?,?) ON CONFLICT(user_id,event_id) DO NOTHING",
        UUID.randomUUID(),
        user,
        event,
        title,
        message,
        category);
  }

  public PageResult<NotificationView> list(UUID owner, int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 100)
      throw ApiException.badRequest("INVALID_PAGE", "Choose a page size from 1 to 100.");
    var content =
        jdbc.query(
            "SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            (r, n) ->
                new NotificationView(
                    r.getObject("id", UUID.class),
                    r.getString("title"),
                    r.getString("message"),
                    r.getString("category"),
                    r.getTimestamp("read_at") != null,
                    r.getTimestamp("created_at").toInstant()),
            owner,
            size,
            page * size);
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM notifications WHERE user_id=?", Long.class, owner);
    return new PageResult<>(content, page, size, count == null ? 0 : count);
  }

  @Transactional
  public void markRead(UUID owner, UUID id) {
    if (jdbc.update(
            "UPDATE notifications SET read_at=coalesce(read_at,now()) WHERE user_id=? AND id=?",
            owner,
            id)
        != 1) throw ApiException.notFound();
  }

  @Transactional
  public void markAllRead(UUID owner) {
    jdbc.update(
        "UPDATE notifications SET read_at=now() WHERE user_id=? AND read_at IS NULL", owner);
  }
}
