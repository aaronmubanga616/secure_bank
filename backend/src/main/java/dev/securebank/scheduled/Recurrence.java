package dev.securebank.scheduled;

import java.time.Instant;
import java.time.ZoneId;

public final class Recurrence {
  public enum Frequency {
    ONCE,
    DAILY,
    WEEKLY,
    MONTHLY
  }

  public static final ZoneId ZONE = ZoneId.of("Africa/Lusaka");

  private Recurrence() {}

  public static Instant next(Instant previous, Frequency frequency, int anchorDay) {
    var local = previous.atZone(ZONE);
    return switch (frequency) {
      case ONCE -> null;
      case DAILY -> local.plusDays(1).toInstant();
      case WEEKLY -> local.plusWeeks(1).toInstant();
      case MONTHLY -> {
        var month = local.plusMonths(1);
        yield month
            .withDayOfMonth(Math.min(anchorDay, month.toLocalDate().lengthOfMonth()))
            .toInstant();
      }
    };
  }

  public static String key(java.util.UUID schedule, Instant due) {
    return "scheduled:" + schedule + ":" + due.toEpochMilli();
  }
}
