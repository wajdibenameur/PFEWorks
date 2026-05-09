package tn.iteam.authservice;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class AuthServiceApplication {

    public static void main(String[] args) {

        // Charger le fichier .env
        Dotenv dotenv = Dotenv.configure()
                .directory(".") // Assurez-vous que le chemin est correct
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        // Injecter dans les variables système pour Spring Boot
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );
        System.out.println("KEYCLOAK URL = " + System.getProperty("KEYCLOAK_BASE_URL"));
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}