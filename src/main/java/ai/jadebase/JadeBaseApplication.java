package ai.jadebase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JadeBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(JadeBaseApplication.class, args);
    }
}
