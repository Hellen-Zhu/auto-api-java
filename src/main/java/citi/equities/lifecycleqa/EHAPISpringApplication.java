package citi.equities.lifecycleqa;

import citi.equities.lifecycleqa.common.config.InitialConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableRetry
public class EHAPISpringApplication {
    public static void main(String[] args) {
        InitialConfig.initEHConfigurationAndOthers();
        SpringApplication springApp = new SpringApplication(EHAPISpringApplication.class);
        springApp.setLogStartupInfo(true);
        springApp.run(args);
    }
}
