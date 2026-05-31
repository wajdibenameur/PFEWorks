package tn.iteam;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
@EnableFeignClients(basePackages = "tn.iteam.auth.client")
@EnableAsync(proxyTargetClass = true)
@EnableScheduling
public class PfeprojectApplication {

    private static final Logger log = LoggerFactory.getLogger(PfeprojectApplication.class);

    public static void main(String[] args) {
        loadEnvironmentVariables();
        SpringApplication.run(PfeprojectApplication.class, args);
    }

    private static void loadEnvironmentVariables() {
        Path envPath = resolveEnvFile();
        if (envPath == null) {
            log.warn("No .env file found for Backend startup, using existing environment values");
            log.info("KEYCLOAK_BASE_URL loaded: {}", isKeycloakBaseUrlLoaded());
            return;
        }

        Dotenv dotenv = Dotenv.configure()
                .directory(envPath.getParent().toString())
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
        log.info("Loaded Backend .env from: {}", envPath.toAbsolutePath());
        log.info("KEYCLOAK_BASE_URL loaded: {}", isKeycloakBaseUrlLoaded());
    }

    private static Path resolveEnvFile() {
        for (String directory : candidateDirectories()) {
            Path envPath = Path.of(directory, ".env");
            if (!Files.exists(envPath)) {
                continue;
            }
            return envPath.toAbsolutePath().normalize();
        }
        return null;
    }

    private static List<String> candidateDirectories() {
        String userDir = System.getProperty("user.dir", ".");
        return List.of(
                userDir,
                Path.of(userDir, "Backend").toString(),
                ".",
                "Backend"
        );
    }

    private static boolean isKeycloakBaseUrlLoaded() {
        String keycloakBaseUrl = System.getProperty("KEYCLOAK_BASE_URL");
        return keycloakBaseUrl != null && !keycloakBaseUrl.isBlank();
    }
}
