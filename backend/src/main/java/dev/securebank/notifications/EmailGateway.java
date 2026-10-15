package dev.securebank.notifications;

public interface EmailGateway {
  void deliver(String recipient, String subject, String message);
}
