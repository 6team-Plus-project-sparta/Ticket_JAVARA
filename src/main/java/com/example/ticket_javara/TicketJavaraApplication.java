package com.example.ticket_javara;

import com.example.ticket_javara.global.config.DotenvInitializer;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@EnableScheduling
public class TicketJavaraApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        boolean osEnvSet = System.getenv("SPRING_PROFILES_ACTIVE") != null;
        boolean jvmArgSet = System.getProperty("spring.profiles.active") != null;

        if (!osEnvSet && !jvmArgSet) {
            String profile = dotenv.get("SPRING_PROFILES_ACTIVE", "local");
            System.setProperty("spring.profiles.active", profile);
        }
        SpringApplication app = new SpringApplication(TicketJavaraApplication.class);
        app.addInitializers(new DotenvInitializer());
        app.run(args);
    }
}
