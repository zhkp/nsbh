package com.kp.nsbh.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldHandleResponseStatusException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());
        exchange.getAttributes().put(RequestIdFilter.REQUEST_ID_KEY, UUID.randomUUID().toString());

        var response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"),
                exchange
        );

        assertEquals(404, response.getBody().status());
        assertEquals("Conversation not found", response.getBody().message());
    }

    @Test
    void shouldFallbackToReasonPhraseWhenReasonMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());
        var response = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST), exchange);
        assertEquals("Bad Request", response.getBody().message());
    }

    @Test
    void shouldHandleValidationVariantsAndUnknown() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());

        var methodValidation = handler.handleValidation(null, exchange);
        var webfluxValidation = handler.handleWebfluxValidation(null, exchange);
        var unknown = handler.handleUnknown(new RuntimeException("x"), exchange);

        assertEquals(400, methodValidation.getBody().status());
        assertEquals(400, webfluxValidation.getBody().status());
        assertEquals(500, unknown.getBody().status());
    }
}
