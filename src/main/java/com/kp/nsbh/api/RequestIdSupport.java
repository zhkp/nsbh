package com.kp.nsbh.api;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public final class RequestIdSupport {
    private RequestIdSupport() {
    }

    public static String currentRequestId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "";
        }
        Object value = attrs.getAttribute(RequestIdFilter.REQUEST_ID_KEY, RequestAttributes.SCOPE_REQUEST);
        return value == null ? "" : value.toString();
    }
}
