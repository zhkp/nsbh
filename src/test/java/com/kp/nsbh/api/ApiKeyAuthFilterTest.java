package com.kp.nsbh.api;

import com.kp.nsbh.config.NsbhProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class ApiKeyAuthFilterTest {

    private WebTestClient client(String apiKey) {
        NsbhProperties properties = new NsbhProperties();
        properties.getApi().setKey(apiKey);
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(properties);
        return WebTestClient
                .bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                .webFilter(filter)
                .build();
    }

    @Test
    void validKeyAllowsV1Request() {
        client("secret").get().uri("/v1/models")
                .header("Authorization", "Bearer secret")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void invalidKeyReturns401WithBody() {
        client("secret").get().uri("/v1/models")
                .header("Authorization", "Bearer wrong")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("invalid_api_key");
    }

    @Test
    void missingAuthHeaderReturns401() {
        client("secret").get().uri("/v1/models")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void authHeaderWithoutBearerPrefixReturns401() {
        client("secret").get().uri("/v1/models")
                .header("Authorization", "Token secret")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void blankConfiguredKeySkipsAuthForV1() {
        client("").get().uri("/v1/models")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void nonV1PathPassesThroughWithoutAuth() {
        client("secret").get().uri("/api/v1/conversations")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void nullConfiguredKeySkipsAuth() {
        NsbhProperties properties = new NsbhProperties();
        properties.getApi().setKey(null);
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(properties);
        WebTestClient wc = WebTestClient
                .bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                .webFilter(filter)
                .build();
        wc.get().uri("/v1/models").exchange().expectStatus().isOk();
    }
}
