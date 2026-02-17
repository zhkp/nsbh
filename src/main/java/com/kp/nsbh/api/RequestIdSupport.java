package com.kp.nsbh.api;

import org.slf4j.MDC;
import org.springframework.web.server.ServerWebExchange;

public final class RequestIdSupport {
    private RequestIdSupport() {
    }

    public static String currentRequestId() {
        String value = MDC.get(RequestIdFilter.REQUEST_ID_KEY);
        return value == null ? "" : value;
    }

    public static String currentRequestId(ServerWebExchange exchange) {
        Object fromExchange = exchange.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        if (fromExchange instanceof String value && !value.isBlank()) {
            return value;
        }
        return currentRequestId();
    }
}
