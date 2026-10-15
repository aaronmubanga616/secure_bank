package dev.securebank.statements;

import dev.securebank.accounts.AccountService;
import dev.securebank.common.ApiException;
import java.io.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class StatementService {
  private final JdbcTemplate jdbc;
  private final AccountService accounts;

  public StatementService(JdbcTemplate jdbc, AccountService accounts) {
    this.jdbc = jdbc;
    this.accounts = accounts;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public byte[] generate(UUID owner, UUID account, LocalDate from, LocalDate to) {
    var view = accounts.get(owner, account);
    String customer =
        ascii(jdbc.queryForObject("SELECT full_name FROM users WHERE id=?", String.class, owner));
    if (customer.length() > 78) customer = customer.substring(0, 75) + "...";
    if (from == null
        || to == null
        || from.isAfter(to)
        || from.plusYears(1).isBefore(to)
        || to.isAfter(LocalDate.now(ZoneId.of("Africa/Lusaka"))))
      throw ApiException.badRequest(
          "INVALID_RANGE", "Choose up to one year ending today or earlier.");
    var zone = ZoneId.of("Africa/Lusaka");
    var start = Timestamp.from(from.atStartOfDay(zone).toInstant());
    var end = Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant());
    String signed = "CASE WHEN e.direction='CREDIT' THEN e.amount ELSE -e.amount END";
    BigDecimal opening =
        jdbc.queryForObject(
            "SELECT coalesce(sum("
                + signed
                + "),0) FROM ledger_entries e JOIN ledger_accounts l ON l.id=e.ledger_account_id JOIN transactions t ON t.id=e.transaction_id WHERE l.bank_account_id=? AND t.completed_at<?",
            BigDecimal.class,
            account,
            start);
    var rows =
        jdbc.queryForList(
            "SELECT t.reference,t.description,t.completed_at,e.direction,e.amount FROM ledger_entries e JOIN ledger_accounts l ON l.id=e.ledger_account_id JOIN transactions t ON t.id=e.transaction_id WHERE l.bank_account_id=? AND t.completed_at>=? AND t.completed_at<? ORDER BY t.completed_at,t.id",
            account,
            start,
            end);
    if (rows.size() > 10000)
      throw ApiException.badRequest("RANGE_TOO_LARGE", "Choose a shorter statement period.");
    BigDecimal balance = opening;
    var lines = new ArrayList<String>();
    lines.add("Date         Description                         Debit        Credit       Balance");
    lines.add(
        "Opening balance                                                              "
            + money(opening));
    var format = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(zone);
    for (var row : rows) {
      BigDecimal amount = (BigDecimal) row.get("amount");
      boolean credit = row.get("direction").equals("CREDIT");
      balance = balance.add(credit ? amount : amount.negate());
      String text = ascii((String) row.get("description"));
      if (text.length() > 30) text = text.substring(0, 27) + "...";
      lines.add(
          String.format(
              Locale.ROOT,
              "%-12s %-30s %12s %12s %13s",
              format.format(((Timestamp) row.get("completed_at")).toInstant()),
              text,
              credit ? "" : money(amount),
              credit ? money(amount) : "",
              money(balance)));
      lines.add("             " + row.get("reference"));
    }
    lines.add(
        "Closing balance                                                              "
            + money(balance));
    try (var document = new PDDocument();
        var output = new ByteArrayOutputStream()) {
      var regular = new PDType1Font(Standard14Fonts.FontName.COURIER);
      var heading = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      int pages = (lines.size() + 35) / 36;
      for (int index = 0; index < pages; index++) {
        var page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (var stream = new PDPageContentStream(document, page)) {
          text(stream, heading, 22, 40, 790, "SecureBank");
          text(stream, heading, 13, 40, 764, "Account statement | ZMW");
          text(stream, regular, 9, 40, 740, "SIMULATED FUNDS - Educational banking simulator");
          text(stream, regular, 9, 40, 724, "Customer: " + customer);
          text(
              stream,
              regular,
              9,
              40,
              708,
              view.type()
                  + " account **** "
                  + view.accountNumber().substring(view.accountNumber().length() - 4));
          text(stream, regular, 9, 40, 692, from + " to " + to + " (Africa/Lusaka)");
          int offset = index * 36;
          for (int line = offset; line < Math.min(offset + 36, lines.size()); line++)
            text(stream, regular, 8, 40, 654 - (line - offset) * 16, lines.get(line));
          text(
              stream,
              regular,
              8,
              40,
              45,
              "Generated " + LocalDate.now(zone) + " | Page " + (index + 1) + " of " + pages);
          text(
              stream,
              regular,
              8,
              40,
              30,
              "Only posted transactions affect your balance. Pending reviews are excluded.");
        }
      }
      document.save(output);
      return output.toByteArray();
    } catch (IOException error) {
      throw new IllegalStateException("Statement generation failed", error);
    }
  }

  private static String money(BigDecimal value) {
    return value.setScale(2).toPlainString();
  }

  private static String ascii(String value) {
    return value.replaceAll("[^\\x20-\\x7E]", "?");
  }

  private static void text(
      PDPageContentStream stream, PDType1Font font, int size, int x, int y, String value)
      throws IOException {
    stream.beginText();
    stream.setFont(font, size);
    stream.newLineAtOffset(x, y);
    stream.showText(value);
    stream.endText();
  }
}
