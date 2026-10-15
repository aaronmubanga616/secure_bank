package dev.securebank.monitoring;
import dev.securebank.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
class MonitoringIT extends IntegrationTestSupport {
    @Test void metricsUseASeparateCredentialAndNeverOpenByDefault()throws Exception{
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").header("Authorization","Bearer wrong-token")).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").header("Authorization","Bearer test-monitoring-token-with-32-characters")).andExpect(status().isOk()).andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }
    @Test void privateResponsesCannotBeCachedAndProtocolErrorsAreSpecific()throws Exception{
        var customer=customer();var actor=user(customer.id().toString()).roles("CUSTOMER");
        mvc.perform(get("/api/v1/accounts").with(actor)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/v1/does-not-exist").with(actor)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(put("/api/v1/accounts").with(actor)).andExpect(status().isMethodNotAllowed());
    }
    @Test void oversizedUtf8PasswordIsRejectedWithoutAnInternalError()throws Exception{
        var customer=customer();mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\""+customer.email()+"\",\"password\":\""+"é".repeat(60)+"\"}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
    @Test void authenticatedApiTrafficHasABoundedRequestWindow()throws Exception{
        var customer=customer();var actor=user(customer.id().toString()).roles("CUSTOMER");for(int request=0;request<120;request++)mvc.perform(get("/api/v1/platform").with(actor)).andExpect(status().isOk());mvc.perform(get("/api/v1/platform").with(actor)).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));
    }
}
