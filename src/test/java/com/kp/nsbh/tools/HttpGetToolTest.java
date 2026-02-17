package com.kp.nsbh.tools;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import org.junit.jupiter.api.Test;

class HttpGetToolTest {

    @Test
    void shouldRejectNonHttpScheme() {
        HttpGetTool tool = new HttpGetTool(new ObjectMapper(), new NsbhProperties());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute("{\"url\":\"ftp://example.com/file\"}").block());
        assertTrue(ex.getMessage().contains("http/https"));
    }

    @Test
    void shouldRejectLocalhost() {
        HttpGetTool tool = new HttpGetTool(new ObjectMapper(), new NsbhProperties());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute("{\"url\":\"http://localhost:8080\"}").block());
        assertTrue(ex.getMessage().contains("Private"));
    }

    @Test
    void shouldRejectLoopbackIpv4() {
        HttpGetTool tool = new HttpGetTool(new ObjectMapper(), new NsbhProperties());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute("{\"url\":\"http://127.0.0.1:8080\"}").block());
        assertTrue(ex.getMessage().contains("Private IP"));
    }

    @Test
    void shouldRejectLoopbackIpv6() {
        HttpGetTool tool = new HttpGetTool(new ObjectMapper(), new NsbhProperties());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute("{\"url\":\"http://[::1]:8080\"}").block());
        assertTrue(ex.getMessage().contains("Private IP"));
    }
}
