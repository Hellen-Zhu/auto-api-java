package citi.equities.lifecycleqa;

import citi.equities.lifecycleqa.common.config.InitialConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableRetry
public class EHAPISpringApplication {
    private static final Logger log = LoggerFactory.getLogger(EHAPISpringApplication.class);

    public static void main(String[] args) {
        InitialConfig.initEHConfigurationAndOthers();
        SpringApplication springApp = new SpringApplication(EHAPISpringApplication.class);
        springApp.setLogStartupInfo(true);
        springApp.run(args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printApplicationUrls(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("server.port", "8084");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String host = "localhost";

        log.info("----------------------------------------------------------");
        log.info("Application '{}' is running!", env.getProperty("spring.application.name"));
        log.info("Access URLs:");
        log.info("  Local:       http://{}:{}{}", host, port, contextPath);
        log.info("  Health:      http://{}:{}{}/actuator/health", host, port, contextPath);
        log.info("  Swagger UI:  http://{}:{}{}/swagger-ui.html", host, port, contextPath);
        log.info("  API Docs:    http://{}:{}{}/v3/api-docs", host, port, contextPath);
        log.info("----------------------------------------------------------");
    }
}
