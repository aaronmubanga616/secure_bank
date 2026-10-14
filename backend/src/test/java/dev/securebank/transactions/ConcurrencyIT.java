package dev.securebank.transactions;

import dev.securebank.IntegrationTestSupport;
import dev.securebank.accounts.AccountService;
import dev.securebank.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import static dev.securebank.transactions.TransactionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConcurrencyIT extends IntegrationTestSupport {
    @Autowired TransactionService transactions;
    @Autowired AccountService accounts;
    @Autowired LedgerService ledger;
    @Autowired LimitService limits;
    private String key() { return UUID.randomUUID().toString(); }
    private List<TransactionView> simultaneously(int count,IntFunction<TransactionView> operation) throws Exception {
        var ready=new CountDownLatch(count); var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(count)) {
            var tasks=new ArrayList<Future<TransactionView>>();
            for(int i=0;i<count;i++) { final int index=i; tasks.add(executor.submit(()->{ready.countDown(); start.await(); return operation.apply(index);})); }
            boolean allReady=ready.await(10,TimeUnit.SECONDS); start.countDown(); assertThat(allReady).isTrue();
            var results=new ArrayList<TransactionView>();
            for(var task:tasks) results.add(task.get(30,TimeUnit.SECONDS));
            return results;
        }
    }

    @Test void onlyOneOfTwoConcurrentWithdrawalsCanSpendTheSameFunds() throws Exception {
        var customer=customer(); var account=accounts.create(customer.id(),AccountService.Type.CURRENT);
        transactions.create(customer.id(),new CreateTransaction(Type.DEPOSIT,null,account.accountNumber(),new BigDecimal("1000"),"Funding"),key());
        var outcomes=simultaneously(2,i->transactions.create(customer.id(),new CreateTransaction(Type.WITHDRAWAL,account.id(),null,new BigDecimal("800"),"Cash"),key()));
        assertThat(outcomes).filteredOn(t->t.status().equals("COMPLETED")).hasSize(1);
        assertThat(outcomes).filteredOn(t->t.status().equals("FAILED")).hasSize(1);
        assertThat(ledger.balance(account.id())).isEqualByComparingTo("200.00");
    }

    @Test void eightSimultaneousRetriesCreateOnlyOneJournal() throws Exception {
        var customer=customer(); var account=accounts.create(customer.id(),AccountService.Type.CURRENT);
        String key=key(); var input=new CreateTransaction(Type.DEPOSIT,null,account.accountNumber(),new BigDecimal("100"),"Funding");
        var outcomes=simultaneously(8,i->transactions.create(customer.id(),input,key));
        assertThat(outcomes.stream().map(TransactionView::id).distinct()).hasSize(1);
        assertThat(outcomes).allSatisfy(t->assertThat(t).isEqualTo(outcomes.getFirst()));
        assertThat(ledger.balance(account.id())).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE transaction_id=?",Integer.class,outcomes.getFirst().id())).isEqualTo(2);
        assertThatThrownBy(()->jdbc.update("UPDATE idempotency_responses SET response_json='{}'::jsonb WHERE user_id=?",customer.id())).isInstanceOf(RuntimeException.class);
    }

    @Test void dailyLimitCannotBeBypassedByConcurrentTransfersFromDifferentAccounts() throws Exception {
        var customer=customer(); var recipient=customer();
        var first=accounts.create(customer.id(),AccountService.Type.CURRENT);
        var second=accounts.create(customer.id(),AccountService.Type.SAVINGS);
        var destination=accounts.create(recipient.id(),AccountService.Type.CURRENT);
        for(var account:List.of(first,second)) transactions.create(customer.id(),new CreateTransaction(Type.DEPOSIT,null,account.accountNumber(),new BigDecimal("150"),"Funding"),key());
        var original=limits.current();
        try {
            limits.update(customer.id(),new LimitService.Limits(original.perTransaction(),new BigDecimal("200"),original.dailyWithdrawal(),original.dailyDeposit()));
            var outcomes=simultaneously(8,i->transactions.create(customer.id(),new CreateTransaction(Type.TRANSFER,i%2==0?first.id():second.id(),destination.accountNumber(),new BigDecimal("60"),"Shared costs"),key()));
            assertThat(outcomes).filteredOn(t->t.status().equals("COMPLETED")).hasSize(3);
            assertThat(ledger.balance(destination.id())).isEqualByComparingTo("180.00");
            assertThat(ledger.balance(first.id()).add(ledger.balance(second.id()))).isEqualByComparingTo("120.00");
        } finally { limits.update(customer.id(),original); }
    }

    @Test void idempotencyKeysAreScopedToEachCustomer() {
        String key=key();
        for(int i=0;i<2;i++) {
            var customer=customer(); var account=accounts.create(customer.id(),AccountService.Type.CURRENT);
            assertThat(transactions.create(customer.id(),new CreateTransaction(Type.DEPOSIT,null,account.accountNumber(),new BigDecimal("10"),"Funding"),key).status()).isEqualTo("COMPLETED");
        }
    }

    @Test void supportAndCustomersCannotChangeLimits() throws Exception {
        String body="{\"perTransaction\":100,\"dailyTransfer\":100,\"dailyWithdrawal\":100,\"dailyDeposit\":100}";
        for(String role:List.of("CUSTOMER","SUPPORT_AGENT","COMPLIANCE_OFFICER"))
            mvc.perform(patch("/api/v1/admin/limits").with(user(UUID.randomUUID().toString()).roles(role)).contentType("application/json").content(body)).andExpect(status().isForbidden());
    }
}
