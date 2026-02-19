package com.kp.nsbh.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class RequestIdFilterUnitTest {

    @Test
    void shouldGenerateRequestIdWhenHeaderMissing() {
        RequestIdFilter filter = new RequestIdFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());
        AtomicReference<String> fromContext = new AtomicReference<>();

        WebFilterChain chain = e -> Mono.deferContextual(ctx -> {
            fromContext.set(ctx.get(RequestIdFilter.REQUEST_ID_KEY));
            return Mono.empty();
        });

        filter.filter(exchange, chain).block();

        String header = exchange.getResponse().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        assertNotNull(header);
        assertFalse(header.isBlank());
        assertEquals(header, fromContext.get());
        assertNull(MDC.get(RequestIdFilter.REQUEST_ID_KEY));
    }

    @Test
    void shouldReuseRequestIdHeaderWhenProvided() {
        RequestIdFilter filter = new RequestIdFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/x").header(RequestIdFilter.REQUEST_ID_HEADER, "req-123").build()
        );

        WebFilterChain chain = e -> Mono.empty();
        filter.filter(exchange, chain).block();

        assertEquals("req-123", exchange.getResponse().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER));
        assertEquals("req-123", exchange.getAttribute(RequestIdFilter.REQUEST_ID_KEY));
    }

    @Test
    void shouldGenerateRequestIdWhenHeaderBlank() {
        RequestIdFilter filter = new RequestIdFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/x").header(RequestIdFilter.REQUEST_ID_HEADER, "   ").build()
        );

        WebFilterChain chain = e -> Mono.empty();
        filter.filter(exchange, chain).block();

        String header = exchange.getResponse().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        assertNotNull(header);
        assertFalse(header.isBlank());
    }
}
