package dev.securebank.accounts;
import dev.securebank.IntegrationTestSupport;
import dev.securebank.transactions.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.math.BigDecimal;
import static dev.securebank.transactions.TransactionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AccountHistoryIT extends IntegrationTestSupport {
    @Autowired AccountService accounts;@Autowired TransactionService transactions;
    @Test void accountHistoryIsScopedToOneOwnedAccount()throws Exception{
        var alice=customer();var bob=customer();var current=accounts.create(alice.id(),AccountService.Type.CURRENT);var savings=accounts.create(alice.id(),AccountService.Type.SAVINGS);
        for(var account:java.util.List.of(current,savings))transactions.create(alice.id(),new CreateTransaction(Type.DEPOSIT,null,account.accountNumber(),new BigDecimal("100"),"Funding"),UUID.randomUUID().toString());
        assertThat(transactions.forAccount(alice.id(),current.id(),0,20).totalElements()).isEqualTo(1);
        mvc.perform(get("/api/v1/accounts/"+current.id()+"/transactions").with(user(bob.id().toString()).roles("CUSTOMER"))).andExpect(status().isNotFound());
    }
}
