package com.kp.nsbh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kp.nsbh.config.NsbhProperties;
import com.kp.nsbh.tools.NsbhTool;
import com.kp.nsbh.tools.Tool;
import com.kp.nsbh.tools.ToolCallStatus;
import com.kp.nsbh.tools.ToolExecutionResult;
import com.kp.nsbh.tools.ToolRegistry;
import com.kp.nsbh.tools.ToolService;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ToolServiceExecuteAllTest {

    @NsbhTool(name = "fast", description = "fast tool", schema = "{}")
    static class FastTool implements Tool {
        @Override
        public Mono<String> execute(String inputJson) {
            return Mono.just("result");
        }
    }

    @Test
    void executeAllRunsBothTools() {
        NsbhProperties props = new NsbhProperties();
        props.getTools().setAllowed(List.of("fast"));

        ToolService service = new ToolService(new ToolRegistry(List.of(new FastTool())), props);

        List<ToolExecutionResult> results = service.executeAll("conv-1", List.of(
                new ToolCallRequest("c1", "fast", "{}"),
                new ToolCallRequest("c2", "fast", "{}")
        )).collectList().block();

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(r -> r.status() == ToolCallStatus.SUCCESS));
        assertTrue(results.stream().anyMatch(r -> "c1".equals(r.toolCallId())));
        assertTrue(results.stream().anyMatch(r -> "c2".equals(r.toolCallId())));
    }

    @Test
    void executeAllWithEmptyListReturnsEmpty() {
        NsbhProperties props = new NsbhProperties();
        ToolService service = new ToolService(new ToolRegistry(List.of()), props);

        List<ToolExecutionResult> results = service.executeAll("conv-1", List.of())
                .collectList().block();

        assertEquals(0, results.size());
    }
}
