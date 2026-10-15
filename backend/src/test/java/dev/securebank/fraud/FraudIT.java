package dev.securebank.fraud;

import static dev.securebank.transactions.TransactionDtos.*;
import static org.assertj.core.api.Assertions.*;

import dev.securebank.IntegrationTestSupport;
import dev.securebank.accounts.AccountService;
import dev.securebank.ledger.LedgerService;
import dev.securebank.transactions.TransactionService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FraudIT extends IntegrationTestSupport {
  @Autowired AccountService accounts;
  @Autowired TransactionService transactions;
  @Autowired FraudReviewService reviews;
  @Autowired LedgerService ledger;

  @Test
  void heldTransfersHaveNoJournalAndCanBeReleasedOnlyOnce() {
    var alice = customer();
    var bob = customer();
    var source = accounts.create(alice.id(), AccountService.Type.CURRENT);
    var target = accounts.create(bob.id(), AccountService.Type.CURRENT);
    transactions.create(
        alice.id(),
        new CreateTransaction(
            Type.DEPOSIT, null, source.accountNumber(), new BigDecimal("40000"), "Funding"),
        UUID.randomUUID().toString());
    var request =
        new CreateTransaction(
            Type.TRANSFER,
            source.id(),
            target.accountNumber(),
            new BigDecimal("25000"),
            "High value transfer");
    String key = UUID.randomUUID().toString();
    var held = transactions.create(alice.id(), request, key);
    assertThat(held.status()).isEqualTo("FLAGGED");
    assertThat(ledger.balance(source.id())).isEqualByComparingTo("40000");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ledger_entries WHERE transaction_id=?",
                Integer.class,
                held.id()))
        .isZero();
    UUID alert =
        jdbc.queryForObject(
            "SELECT id FROM fraud_alerts WHERE transaction_id=?", UUID.class, held.id());
    reviews.review(
        bob.id(),
        alert,
        new FraudReviewService.Review(
            "FALSE_POSITIVE", "Customer confirmed this simulator transfer."));
    assertThat(transactions.get(alice.id(), held.id()).status()).isEqualTo("COMPLETED");
    assertThat(ledger.balance(source.id())).isEqualByComparingTo("15000");
    assertThat(ledger.balance(target.id())).isEqualByComparingTo("25000");
    assertThat(transactions.create(alice.id(), request, key).status()).isEqualTo("FLAGGED");
    assertThatThrownBy(
            () ->
                reviews.review(
                    bob.id(),
                    alert,
                    new FraudReviewService.Review(
                        "RESOLVED", "Try to release this transfer twice.")))
        .isInstanceOf(dev.securebank.common.ApiException.class);
  }

  @Test
  void confirmedFraudNeverMovesMoney() {
    var alice = customer();
    var bob = customer();
    var source = accounts.create(alice.id(), AccountService.Type.CURRENT);
    var target = accounts.create(bob.id(), AccountService.Type.CURRENT);
    transactions.create(
        alice.id(),
        new CreateTransaction(
            Type.DEPOSIT, null, source.accountNumber(), new BigDecimal("30000"), "Funding"),
        UUID.randomUUID().toString());
    var held =
        transactions.create(
            alice.id(),
            new CreateTransaction(
                Type.TRANSFER,
                source.id(),
                target.accountNumber(),
                new BigDecimal("25000"),
                "Review me"),
            UUID.randomUUID().toString());
    UUID alert =
        jdbc.queryForObject(
            "SELECT id FROM fraud_alerts WHERE transaction_id=?", UUID.class, held.id());
    reviews.review(
        bob.id(),
        alert,
        new FraudReviewService.Review(
            "CONFIRMED_FRAUD", "Confirmed by the simulator investigator."));
    assertThat(transactions.get(alice.id(), held.id()).failureReason())
        .isEqualTo("FRAUD_CONFIRMED");
    assertThat(ledger.balance(source.id())).isEqualByComparingTo("30000");
    assertThat(ledger.balance(target.id())).isEqualByComparingTo("0");
  }
}
