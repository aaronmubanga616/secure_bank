package dev.securebank.statements;

import static dev.securebank.transactions.TransactionDtos.*;
import static org.assertj.core.api.Assertions.*;

import dev.securebank.IntegrationTestSupport;
import dev.securebank.accounts.AccountService;
import dev.securebank.notifications.NotificationService;
import dev.securebank.transactions.TransactionService;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class StatementIT extends IntegrationTestSupport {
  @Autowired StatementService statements;
  @Autowired TransactionService transactions;
  @Autowired AccountService accounts;
  @Autowired NotificationService notifications;
  @Autowired TransactionTemplate transaction;

  @Test
  void statementsContainPostedBalancesAndRejectForeignAccounts() throws Exception {
    var user = customer();
    var stranger = customer();
    var account = accounts.create(user.id(), AccountService.Type.CURRENT);
    transactions.create(
        user.id(),
        new CreateTransaction(
            Type.DEPOSIT, null, account.accountNumber(), new BigDecimal("1234.56"), "Test deposit"),
        UUID.randomUUID().toString());
    transactions.create(
        user.id(),
        new CreateTransaction(
            Type.WITHDRAWAL, account.id(), null, new BigDecimal("34.56"), "Test withdrawal"),
        UUID.randomUUID().toString());
    LocalDate today = LocalDate.now(ZoneId.of("Africa/Lusaka"));
    byte[] pdf = statements.generate(user.id(), account.id(), today, today);
    try (var document = Loader.loadPDF(pdf)) {
      String text = new PDFTextStripper().getText(document);
      assertThat(text)
          .contains(
              "SIMULATED FUNDS",
              "Test Customer",
              "Opening balance",
              "Closing balance",
              "1234.56",
              "1200.00")
          .doesNotContain(account.accountNumber(), user.email());
    }
    assertThatThrownBy(() -> statements.generate(stranger.id(), account.id(), today, today))
        .isInstanceOf(dev.securebank.common.ApiException.class);
    java.nio.file.Files.write(java.nio.file.Path.of("target", "statement-sample.pdf"), pdf);
  }

  @Test
  void notificationReadStateIsOwnedAndDuplicateDeliveryIsHarmless() {
    var user = customer();
    var stranger = customer();
    UUID event = UUID.randomUUID();
    transaction.executeWithoutResult(
        status -> {
          notifications.add(user.id(), event, "Hello", "Test notification", "ACCOUNT");
          notifications.add(user.id(), event, "Hello", "Test notification", "ACCOUNT");
        });
    var list = notifications.list(user.id(), 0, 20);
    assertThat(list.totalElements()).isEqualTo(1);
    UUID id = list.content().getFirst().id();
    assertThatThrownBy(() -> notifications.markRead(stranger.id(), id))
        .isInstanceOf(dev.securebank.common.ApiException.class);
    assertThat(notifications.list(user.id(), 0, 20).content().getFirst().read()).isFalse();
    notifications.markRead(user.id(), id);
    assertThat(notifications.list(user.id(), 0, 20).content().getFirst().read()).isTrue();
  }

  @Test
  void longerStatementsPaginateWithoutLosingTheirClosingBalance() throws Exception {
    var user = customer();
    var account = accounts.create(user.id(), AccountService.Type.SAVINGS);
    for (int index = 0; index < 20; index++)
      transactions.create(
          user.id(),
          new CreateTransaction(
              Type.DEPOSIT,
              null,
              account.accountNumber(),
              new BigDecimal("1.00"),
              "Statement row " + index),
          UUID.randomUUID().toString());
    LocalDate today = LocalDate.now(ZoneId.of("Africa/Lusaka"));
    byte[] pdf = statements.generate(user.id(), account.id(), today, today);
    try (var document = Loader.loadPDF(pdf)) {
      assertThat(document.getNumberOfPages()).isEqualTo(2);
      var text = new PDFTextStripper().getText(document);
      assertThat(text).contains("Page 1 of 2", "Page 2 of 2", "Closing balance", "20.00");
      var secondPage = new PDFTextStripper();
      secondPage.setStartPage(2);
      secondPage.setEndPage(2);
      assertThat(secondPage.getText(document))
          .contains("Date", "Description", "Debit", "Credit", "Balance");
    }
    java.nio.file.Files.write(java.nio.file.Path.of("target", "statement-multipage.pdf"), pdf);
  }
}
