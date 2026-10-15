package dev.securebank.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class ApiDocumentation {
  @Bean
  OpenAPI bankingApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("SecureBank simulator API")
                .version("0.1.0")
                .description(
                    "Educational ZMW banking. Every amount represents simulated funds. Supply an Idempotency-Key for money movements; fetch a receipt for its current status."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearer",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearer"));
  }

  @Bean
  OpenApiCustomizer publicOperations() {
    return api -> {
      if (api.getPaths() == null) return;
      api.getPaths()
          .forEach(
              (path, item) -> {
                if (path.equals("/api/v1/platform")
                    || java.util.Set.of(
                            "/api/v1/auth/register",
                            "/api/v1/auth/login",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/logout",
                            "/api/v1/auth/verify-email",
                            "/api/v1/auth/request-verification",
                            "/api/v1/auth/forgot-password",
                            "/api/v1/auth/reset-password",
                            "/api/v1/auth/mfa/verify")
                        .contains(path))
                  item.readOperations()
                      .forEach(operation -> operation.setSecurity(java.util.List.of()));
              });
    };
  }
}
