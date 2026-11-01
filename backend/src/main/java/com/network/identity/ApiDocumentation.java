package com.network.identity;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ApiDocumentation {
    @Bean OpenAPI contract() {
        return new OpenAPI().info(new Info().title("Network identity BFF").version("1.0.0")
            .description("Server-side OIDC sessions. Credential, verification and MFA ceremonies use Keycloak hosted flows. Mutations require the session-bound CSRF token. Organization operations reload membership and policy. See docs/api.md for rate limits and retry semantics."))
            .components(new Components()
                .addSecuritySchemes("session",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("PLATFORM_SESSION"))
                .addSecuritySchemes("csrf",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-CSRF-TOKEN")))
            .addSecurityItem(new SecurityRequirement().addList("session"));
    }
    @Bean OpenApiCustomizer mutationSecurity() {
        return api -> api.getPaths().forEach((path,item) -> item.readOperationsMap().forEach((method,operation) -> {
            boolean anonymous=path.equals("/api/v1/auth/csrf")||path.equals("/api/v1/auth/providers")||path.equals("/api/v1/auth/sso/discovery")||path.equals("/api/v1/auth/invitations/prepare");
            var requirement=new SecurityRequirement();if(!anonymous)requirement.addList("session");
            if(method!=io.swagger.v3.oas.models.PathItem.HttpMethod.GET)requirement.addList("csrf");
            operation.setSecurity(requirement.isEmpty()?java.util.List.of():java.util.List.of(requirement));
        }));
    }
}
