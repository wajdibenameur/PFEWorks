package tn.iteam.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import tn.iteam.auth.config.FeignFormConfig;
import tn.iteam.auth.dto.TokenResponse;

@FeignClient(
        name = "keycloak-token-client",
        url = "${keycloak.base-url}",
        configuration = FeignFormConfig.class
)
public interface KeycloakTokenClient {

    @PostMapping(
            value = "/realms/{realm}/protocol/openid-connect/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    TokenResponse obtainToken(
            @PathVariable("realm") String realm,
            @RequestBody MultiValueMap<String, String> formParams
    );
}
