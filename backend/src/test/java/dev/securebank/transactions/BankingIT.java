package dev.securebank.transactions;

import dev.securebank.IntegrationTestSupport;
import dev.securebank.accounts.AccountService;
import dev.securebank.common.ApiException;
import dev.securebank.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.UUID;
import static dev.securebank.transactions.TransactionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BankingIT extends IntegrationTestSupport {
    @Autowired TransactionService transactions;
    @Autowired AccountService accounts;
    @Autowired LedgerService ledger;
    private String key() { return UUID.randomUUID().toString(); }

    @Test void depositsTransfersAndWithdrawalsPostExactBalancedAmounts() {
        var alice=customer(); var bob=customer();
        var source=accounts.create(alice.id(),AccountService.Type.CURRENT);
        var destination=accounts.create(bob.id(),AccountService.Type.SAVINGS);
        assertThat(transactions.create(alice.id(),new CreateTransaction(Type.DEPOSIT,null,source.accountNumber(),new BigDecimal("1000"),"Simulated salary"),key()).status()).isEqualTo("COMPLETED");
        var transfer=transactions.create(alice.id(),new CreateTransaction(Type.TRANSFER,source.id(),destination.accountNumber(),new BigDecimal("250.25"),"Shared lunch"),key());
        transactions.create(alice.id(),new CreateTransaction(Type.WITHDRAWAL,source.id(),null,new BigDecimal("49.75"),"Simulated cash"),key());
        assertThat(ledger.balance(source.id())).isEqualByComparingTo("700.00");
        assertThat(ledger.balance(destination.id())).isEqualByComparingTo("250.25");
        assertThat(transactions.get(bob.id(),transfer.id()).direction()).isEqualTo("IN");
        assertThat(transactions.list(bob.id(),0,20,"lunch",Status.COMPLETED,null,null,null,null).totalElements()).isEqualTo(1);
        assertThatThrownBy(()->transactions.get(customer().id(),transfer.id())).isInstanceOf(ApiException.class);
    }

    @Test void retriesReturnTheOriginalTransactionAndDifferentPayloadsConflict() {
        var customer=customer(); var account=accounts.create(customer.id(),AccountService.Type.CURRENT);
        String key=key();
        var request=new CreateTransaction(Type.DEPOSIT,null,account.accountNumber(),new BigDecimal("100.00"),"Funding");
        var original=transactions.create(customer.id(),request,key);
        assertThat(transactions.create(customer.id(),request,key).id()).isEqualTo(original.id());
        assertThat(ledger.balance(account.id())).isEqualByComparingTo("100.00");
        assertThatThrownBy(()->transactions.create(customer.id(),new CreateTransaction(Type.DEPOSIT,null,account.accountNumber(),new BigDecimal("200"),"Funding"),key))
            .isInstanceOf(ApiException.class).extracting(e->((ApiException)e).code()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test void failedSpendingStaysFailedOnRetryEvenAfterFundsArrive() {
        var customer=customer(); var account=accounts.create(customer.id(),AccountService.Type.CURRENT);
        String key=key(); var withdrawal=new CreateTransaction(Type.WITHDRAWAL,account.id(),null,new BigDecimal("10"),"Cash");
        var failed=transactions.create(customer.id(),withdrawal,key);
        assertThat(failed.status()).isEqualTo("FAILED"); assertThat(failed.failureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        transactions.create(customer.id(),new CreateTransaction(Type.DEPOSIT,null,account.accountNumber(),new BigDecimal("100"),"Funding"),key());
        assertThat(transactions.create(customer.id(),withdrawal,key)).isEqualTo(failed);
        assertThat(ledger.balance(account.id())).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE transaction_id=?",Integer.class,failed.id())).isZero();
    }

    @Test void aCustomerCannotSpendFromOrDepositToAnotherCustomersAccount() {
        var alice=customer(); var bob=customer(); var account=accounts.create(alice.id(),AccountService.Type.CURRENT);
        assertThatThrownBy(()->transactions.create(bob.id(),new CreateTransaction(Type.WITHDRAWAL,account.id(),null,new BigDecimal("1"),"Cash"),key())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->transactions.create(bob.id(),new CreateTransaction(Type.DEPOSIT,null,account.accountNumber(),new BigDecimal("1"),"Funding"),key())).isInstanceOf(ApiException.class);
        assertThat(ledger.balance(account.id())).isEqualByComparingTo("0.00");
    }

    @Test void httpValidationRejectsExtraCentsAndRequiresAnIdempotencyKey() throws Exception {
        var customer=customer(); var account=accounts.create(customer.id(),AccountService.Type.CURRENT);
        String body="{\"type\":\"DEPOSIT\",\"destinationAccountNumber\":\""+account.accountNumber()+"\",\"amount\":\"1.001\",\"description\":\"Funding\"}";
        mvc.perform(post("/api/v1/transactions").with(user(customer.id().toString()).roles("CUSTOMER"))
            .contentType("application/json").header("Idempotency-Key",key()).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/transactions").with(user(customer.id().toString()).roles("CUSTOMER"))
            .contentType("application/json").content(body.replace("1.001","1.00"))).andExpect(status().isBadRequest());
        assertThat(ledger.balance(account.id())).isEqualByComparingTo("0.00");
    }
}
