package com.documind.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI docuMindOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DocuMind API")
                        .version("1.0.0")
                        .description("""
                                Multi-tenant document intelligence platform API.

                                All business endpoints require a bearer access token. The active
                                workspace is selected with the `X-Workspace-Id` header; when the header
                                is omitted, the first workspace of the authenticated user is used.
                                """))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
