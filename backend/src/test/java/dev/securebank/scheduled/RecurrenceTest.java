package dev.securebank.scheduled;

import static dev.securebank.scheduled.Recurrence.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class RecurrenceTest {
  @Test
  void monthlyTransfersKeepTheirOriginalAnchorAfterShortMonths() {
    var january = ZonedDateTime.of(2026, 1, 31, 9, 0, 0, 0, ZONE).toInstant();
    var february = next(january, Frequency.MONTHLY, 31);
    assertThat(february.atZone(ZONE).getDayOfMonth()).isEqualTo(28);
    assertThat(next(february, Frequency.MONTHLY, 31).atZone(ZONE).getDayOfMonth()).isEqualTo(31);
    assertThat(february.atZone(ZONE).getHour()).isEqualTo(9);
  }

  @Test
  void recurrenceHandlesLeapYearsAndOneTimePayments() {
    var january = ZonedDateTime.of(2028, 1, 31, 9, 0, 0, 0, ZONE).toInstant();
    assertThat(next(january, Frequency.MONTHLY, 31).atZone(ZONE).getDayOfMonth()).isEqualTo(29);
    assertThat(next(january, Frequency.ONCE, 31)).isNull();
  }
}
