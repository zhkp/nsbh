package com.kp.nsbh.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonLogFormatter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonLogFormatter() {
    }

    public static String json(Map<String, Object> fields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            return "{\"event\":\"log_serialize_error\"}";
        }
    }

    public static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (key != null) {
                map.put(String.valueOf(key), value);
            }
        }
        return map;
    }
}
