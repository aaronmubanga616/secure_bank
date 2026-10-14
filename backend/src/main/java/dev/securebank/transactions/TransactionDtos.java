package dev.securebank.transactions;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TransactionDtos {
    private TransactionDtos() {}
    public enum Type { DEPOSIT,WITHDRAWAL,TRANSFER }
    public enum Status { PENDING,PROCESSING,COMPLETED,FAILED,REVERSED,FLAGGED }
    public record CreateTransaction(@NotNull Type type,UUID sourceAccount,@Size(max=20) String destinationAccountNumber,
        @NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal amount,
        @NotBlank @Size(max=160) String description) {}
    public record TransactionView(UUID id,String reference,String type,String amount,String currency,UUID sourceAccount,
        UUID destinationAccount,String sourceAccountNumber,String destinationAccountNumber,String status,String description,
        String direction,int riskScore,String failureReason,Instant createdAt,Instant completedAt) {}
}
