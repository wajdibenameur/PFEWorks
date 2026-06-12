package tn.iteam.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "API de supervision et de gestion des tickets",
                version = "1.0",
                description = "Documentation des API de supervision, d'agregation des sources, de gestion des tickets, "
                        + "de collecte manuelle et de prediction ML.",
                contact = @Contact(name = "Equipe ITEAM"),
                license = @License(name = "Usage interne")
        ),
        servers = {
                @Server(url = "/", description = "Serveur courant")
        }
)
public class OpenApiConfig {
}
