package tn.iteam.auth.util;

import feign.Response;
import tn.iteam.auth.exception.KeycloakIntegrationException;

import java.util.List;

public final class KeycloakLocationUtils {

    private KeycloakLocationUtils() {
    }

    public static String extractUserIdFromLocation(Response response) {
        String location = response.headers().getOrDefault("Location",
                response.headers().getOrDefault("location", List.of()))
                .stream().findFirst()
                .orElseThrow(() -> new KeycloakIntegrationException(
                        "User created but Location header missing in Keycloak response"));
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
