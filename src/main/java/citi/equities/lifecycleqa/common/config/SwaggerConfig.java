package citi.equities.lifecycleqa.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EH API Service")
                        .version("1.0")
                        .description("EH API Documentation for Test Automation")
                        .contact(new Contact()
                                .name("EH Team")
                                .email("eh-team@citi.com")));
    }
}
