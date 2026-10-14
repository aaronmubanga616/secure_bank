package dev.securebank.common;

import dev.securebank.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PlatformController.class, properties = "securebank.cors-origins=http://localhost:5173")
@Import(SecurityConfig.class)
class ApiSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;

    @Test void publicMetadataHasSecurityAndCorrelationHeaders() throws Exception {
        mvc.perform(get("/api/v1/platform")).andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("EDUCATIONAL_SIMULATOR"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test void unconfiguredRoutesAreDeniedByDefault() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test @WithMockUser(roles="CUSTOMER")
    void customerCannotReadStaffResources() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isForbidden());
    }

    @Test void rejectsUntrustedCorsOrigins() throws Exception {
        mvc.perform(get("/api/v1/platform").header("Origin", "https://untrusted.example"))
            .andExpect(status().isForbidden());
    }
}
