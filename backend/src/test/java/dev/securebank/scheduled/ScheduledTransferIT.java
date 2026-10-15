package dev.securebank.scheduled;

import static org.assertj.core.api.Assertions.*;

import dev.securebank.IntegrationTestSupport;
import dev.securebank.accounts.AccountService;
import dev.securebank.beneficiaries.BeneficiaryService;
import dev.securebank.common.ApiException;
import dev.securebank.ledger.LedgerService;
import dev.securebank.transactions.TransactionDtos.*;
import dev.securebank.transactions.TransactionService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ScheduledTransferIT extends IntegrationTestSupport {
  @Autowired BeneficiaryService beneficiaries;
  @Autowired ScheduleService schedules;
  @Autowired ScheduleExecutor executor;
  @Autowired ScheduledPaymentRunner runner;
  @Autowired TransactionService transactions;
  @Autowired AccountService accounts;
  @Autowired LedgerService ledger;

  record Fixture(
      UUID owner,
      AccountService.AccountView source,
      AccountService.AccountView destination,
      UUID beneficiary,
      UUID schedule,
      Instant due) {}

  private Fixture fixture() {
    var owner = customer();
    var recipient = customer();
    var source = accounts.create(owner.id(), AccountService.Type.CURRENT);
    var destination = accounts.create(recipient.id(), AccountService.Type.CURRENT);
    transactions.create(
        owner.id(),
        new CreateTransaction(
            Type.DEPOSIT, null, source.accountNumber(), new BigDecimal("100"), "Funding"),
        UUID.randomUUID().toString());
    var beneficiary =
        beneficiaries.create(
            owner.id(),
            new BeneficiaryService.CreateBeneficiary(
                "Recipient", destination.accountNumber(), PASSWORD, null));
    var schedule =
        schedules.create(
            owner.id(),
            new ScheduleService.CreateSchedule(
                source.id(),
                beneficiary.id(),
                new BigDecimal("10"),
                "Scheduled costs",
                Recurrence.Frequency.ONCE,
                Instant.now().plusSeconds(60),
                PASSWORD,
                null));
    Instant due = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MILLIS);
    jdbc.update(
        "UPDATE scheduled_transfers SET next_run_at=? WHERE id=?",
        Timestamp.from(due),
        schedule.id());
    return new Fixture(owner.id(), source, destination, beneficiary.id(), schedule.id(), due);
  }

  @Test
  void savedRecipientsAndSchedulesArePrivate() {
    var fixture = fixture();
    var stranger = customer();
    assertThat(beneficiaries.list(stranger.id())).isEmpty();
    assertThat(schedules.list(stranger.id())).isEmpty();
    assertThatThrownBy(() -> beneficiaries.delete(stranger.id(), fixture.beneficiary()))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> schedules.change(stranger.id(), fixture.schedule(), "CANCELLED"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void deletingARecipientCancelsTheirPendingSchedules() {
    var fixture = fixture();
    beneficiaries.delete(fixture.owner(), fixture.beneficiary());
    executor.execute(fixture.schedule());
    assertThat(schedules.get(fixture.owner(), fixture.schedule()).status()).isEqualTo("CANCELLED");
    assertThat(ledger.balance(fixture.source().id())).isEqualByComparingTo("100.00");
  }

  @Test
  void workerRecoveryAfterMoneyCommittedCannotPayTheOccurrenceTwice() {
    var fixture = fixture();
    runner.pay(
        fixture.owner(),
        fixture.schedule(),
        fixture.due(),
        fixture.source().id(),
        fixture.destination().accountNumber(),
        new BigDecimal("10"),
        "Scheduled costs");
    executor.execute(fixture.schedule());
    executor.execute(fixture.schedule());
    assertThat(ledger.balance(fixture.source().id())).isEqualByComparingTo("90.00");
    assertThat(ledger.balance(fixture.destination().id())).isEqualByComparingTo("10.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM scheduled_occurrences WHERE schedule_id=?",
                Integer.class,
                fixture.schedule()))
        .isEqualTo(1);
    assertThat(schedules.get(fixture.owner(), fixture.schedule()).status()).isEqualTo("COMPLETED");
  }

  @Test
  void twoWorkersCannotExecuteTheSameScheduleTwice() throws Exception {
    var fixture = fixture();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> executor.execute(fixture.schedule()));
      var second = pool.submit(() -> executor.execute(fixture.schedule()));
      first.get();
      second.get();
    }
    assertThat(ledger.balance(fixture.destination().id())).isEqualByComparingTo("10.00");
  }

  @Test
  void addingARecipientRequiresPasswordConfirmation() {
    var owner = customer();
    var recipient = customer();
    var account = accounts.create(recipient.id(), AccountService.Type.SAVINGS);
    assertThatThrownBy(
            () ->
                beneficiaries.create(
                    owner.id(),
                    new BeneficiaryService.CreateBeneficiary(
                        "Recipient", account.accountNumber(), "wrong", null)))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).code())
        .isEqualTo("VERIFICATION_FAILED");
    assertThat(beneficiaries.list(owner.id())).isEmpty();
  }
}
