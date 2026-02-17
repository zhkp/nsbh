package com.kp.nsbh.api;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RequestIdFilter implements WebFilter {
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        exchange.getAttributes().put(REQUEST_ID_KEY, requestId);
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_KEY, requestId);
        String finalRequestId = requestId;
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(REQUEST_ID_KEY, finalRequestId))
                .doFinally(signalType -> MDC.remove(REQUEST_ID_KEY));
    }
}
