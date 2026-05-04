package com.kp.nsbh.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ThinkToolTest {

    @Test
    void returnsThoughtAndStatusOk() {
        String result = new ThinkTool().execute("{\"thought\":\"I need to check the time\"}").block();
        assertTrue(result.contains("I need to check the time"));
        assertTrue(result.contains("ok"));
    }

    @Test
    void missingThoughtReturnsStatusOk() {
        String result = new ThinkTool().execute("{}").block();
        assertTrue(result.contains("ok"));
    }

    @Test
    void nullInputReturnsStatusOk() {
        String result = new ThinkTool().execute(null).block();
        assertTrue(result.contains("ok"));
    }
}
