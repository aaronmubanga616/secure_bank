package dev.securebank.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailGateway implements EmailGateway {
  private final JavaMailSender sender;
  private final String from;

  public SmtpEmailGateway(JavaMailSender sender, @Value("${securebank.mail-from}") String from) {
    this.sender = sender;
    this.from = from;
  }

  @Override
  public void deliver(String recipient, String subject, String message) {
    var mail = new SimpleMailMessage();
    mail.setFrom(from);
    mail.setTo(recipient);
    mail.setSubject(subject);
    mail.setText(message);
    sender.send(mail);
  }
}
