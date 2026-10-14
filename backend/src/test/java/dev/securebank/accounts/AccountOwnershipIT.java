package dev.securebank.accounts;

import dev.securebank.IntegrationTestSupport;
import dev.securebank.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountOwnershipIT extends IntegrationTestSupport {
    @Autowired AccountService accounts;
    @Test void customersCannotReadAnotherCustomersAccountByChangingTheId() throws Exception {
        var alice=customer(); var bob=customer();
        var account=accounts.create(alice.id(),AccountService.Type.SAVINGS);
        assertThat(accounts.list(bob.id())).isEmpty();
        assertThatThrownBy(()->accounts.get(bob.id(),account.id())).isInstanceOf(ApiException.class)
            .extracting(e->((ApiException)e).status().value()).isEqualTo(404);
        mvc.perform(get("/api/v1/accounts/"+account.id()).with(user(bob.id().toString()).roles("CUSTOMER")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accounts/"+account.id()).with(user(alice.id().toString()).roles("CUSTOMER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("ZMW"));
    }
    @Test void anAccountTypeCanOnlyBeOpenedOnce() {
        var customer=customer();
        accounts.create(customer.id(),AccountService.Type.CURRENT);
        assertThatThrownBy(()->accounts.create(customer.id(),AccountService.Type.CURRENT)).isInstanceOf(ApiException.class)
            .extracting(e->((ApiException)e).code()).isEqualTo("ACCOUNT_EXISTS");
        assertThat(accounts.list(customer.id())).hasSize(1);
    }
    @Test void profilesNeverExposePasswordHashesOrMfaSecrets() throws Exception {
        var customer=customer();
        mvc.perform(get("/api/v1/users/me").with(user(customer.id().toString()).roles("CUSTOMER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(customer.email()))
            .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.mfaSecret").doesNotExist());
    }
}
