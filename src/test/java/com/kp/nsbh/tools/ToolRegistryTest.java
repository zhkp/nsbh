package com.kp.nsbh.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kp.nsbh.config.NsbhProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    @Test
    void shouldDiscoverAnnotatedTools() {
        ToolRegistry registry = new ToolRegistry(List.of(
                new TimeTool(),
                new HttpGetTool(new ObjectMapper(), new NsbhProperties())
        ));

        List<ToolMetadata> all = registry.listMetadata();
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(m -> "time".equals(m.name())));
        assertTrue(all.stream().anyMatch(m -> "http_get".equals(m.name())));

        ToolMetadata metadata = registry.findMetadata("time");
        assertEquals("Returns current server time in ISO-8601 format", metadata.description());
        assertEquals("{}", metadata.schema());
        assertEquals(List.of(), metadata.requiredPermissions());
        assertNotNull(registry.findTool("time"));
        assertNotNull(registry.findTool("http_get"));
    }

    @Test
    void shouldFailWhenToolMissingAnnotation() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ToolRegistry(List.of(new MissingAnnotationTool())));
        assertTrue(ex.getMessage().contains("missing @NsbhTool"));
    }

    @Test
    void shouldFailWhenToolNameDuplicated() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ToolRegistry(List.of(new DuplicateOne(), new DuplicateTwo())));
        assertTrue(ex.getMessage().contains("Duplicate tool name"));
    }

    static class MissingAnnotationTool implements Tool {
        @Override
        public reactor.core.publisher.Mono<String> execute(String inputJson) {
            return reactor.core.publisher.Mono.just("x");
        }
    }

    @NsbhTool(name = "dup", description = "one")
    static class DuplicateOne implements Tool {
        @Override
        public reactor.core.publisher.Mono<String> execute(String inputJson) {
            return reactor.core.publisher.Mono.just("1");
        }
    }

    @NsbhTool(name = "dup", description = "two")
    static class DuplicateTwo implements Tool {
        @Override
        public reactor.core.publisher.Mono<String> execute(String inputJson) {
            return reactor.core.publisher.Mono.just("2");
        }
    }
}
