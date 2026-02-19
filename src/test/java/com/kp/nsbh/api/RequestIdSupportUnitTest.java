package com.kp.nsbh.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RequestIdSupportUnitTest {

    @Test
    void shouldReadFromExchangeAttributeFirst() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());
        exchange.getAttributes().put(RequestIdFilter.REQUEST_ID_KEY, "req-exchange");
        MDC.put(RequestIdFilter.REQUEST_ID_KEY, "req-mdc");
        try {
            assertEquals("req-exchange", RequestIdSupport.currentRequestId(exchange));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void shouldFallbackToMdcWhenExchangeAttributeMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());
        MDC.put(RequestIdFilter.REQUEST_ID_KEY, "req-mdc");
        try {
            assertEquals("req-mdc", RequestIdSupport.currentRequestId(exchange));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void shouldReturnEmptyWhenNoRequestIdAvailable() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());
        MDC.clear();
        assertEquals("", RequestIdSupport.currentRequestId(exchange));
        assertEquals("", RequestIdSupport.currentRequestId());
    }
}
