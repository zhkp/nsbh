package com.kp.nsbh.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonLogFormatterTest {

    @Test
    void fieldsShouldIgnoreNullKeyAndKeepOrder() {
        Map<String, Object> map = JsonLogFormatter.fields("a", 1, null, "x", "b", 2);
        assertEquals(2, map.size());
        assertEquals(1, map.get("a"));
        assertEquals(2, map.get("b"));
    }

    @Test
    void jsonShouldFallbackWhenSerializationFails() {
        Map<String, Object> map = new HashMap<>();
        map.put("self", map);
        String json = JsonLogFormatter.json(map);
        assertTrue(json.contains("log_serialize_error"));
    }
}
